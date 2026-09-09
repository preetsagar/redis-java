# Project revision — what I built and why

A walkthrough of the whole Redis server, subsystem by subsystem, in the order it
was built. For each: **what** it does, **why** it's built that way, the **tricky
bits** (including bugs I hit), and a one-line **interview soundbite**.

Companion docs: [`architecture.md`](architecture.md) (component ownership +
diagrams), [`interview-prep.md`](interview-prep.md) (the drill sheet — pitch,
soundbites, Q&A), [`../README.md`](../README.md) (feature list). Knowledge graph
in `../graphify-out/` — `graphify query "<question>"` for a scoped subgraph.

---

## 0. The shape of the whole thing

```
Main.parse(args)                       # --port, --replicaof, --dir, --dbfilename, --appendonly...
  └─ RedisServer.start()
       ├─ Database            (stringStore + listStore + streamStore + sortedSetStore)
       ├─ RdbReader.loadInto  (populate stringStore from dump.rdb if present)
       ├─ Aof.open            (lay out appendonlydir/, find active file)
       ├─ CommandDispatcher   (one, shared) ── builds ── CommandRegistry (name → handler)
       ├─ aof.replay          (rebuild state from the AOF before accepting clients)
       ├─ ReplicationClient   (only if --replicaof: connect to master on a daemon thread)
       └─ accept loop: new Thread(new ClientHandler(socket, dispatcher))
```

Per connection: one `ClientHandler` thread → one `ClientSession` (its MULTI /
queue / WATCH / subscription / auth state). The handler is a thin loop:
`RespParser` bytes → `List<String>`, `dispatcher.dispatch()` → `byte[]`, write
back.

**Why one dispatcher, many sessions:** command *logic* is stateless and shared;
*connection* state (am I in a transaction? which channels am I subscribed to?) is
per-socket. Splitting them means the shared part needs no per-connection
bookkeeping and the per-connection part needs no locking (only its own thread
touches it).

---

## 1. RESP protocol + basic commands

**Stages:** maven build → multiple commands on one connection → multiple clients →
ECHO → SET/GET → expiry.

**What:** `RespParser` reads a RESP array-of-bulk-strings command into
`List<String>`. `RespEncoder` builds every reply type — simple string (`+OK\r\n`),
bulk string (`$3\r\nfoo\r\n`), integer (`:5\r\n`), array (`*2\r\n...`), error
(`-ERR ...\r\n`), null bulk (`$-1\r\n`), null array (`*-1\r\n`).

**Why:** RESP is length-prefixed and line-oriented, so a hand-rolled parser is
~40 lines and has no ambiguity. Every command handler returns `byte[]` (already
encoded) rather than some reply object — the dispatcher just concatenates and
writes, no serialization layer.

**Tricky bits:**
- **Concurrency from the start:** thread-per-connection. Simple mental model, and
  the challenge never pushes connection counts where that hurts.
- **TTL is lazy:** `SET key val EX 10` stores an absolute expiry timestamp; `GET`
  checks it and deletes on read. No background sweeper — nothing tests active
  expiry, and lazy eviction is what real Redis does for the common path anyway.

**Soundbite:** "RESP is length-prefixed, so the parser is trivial; every handler
returns raw encoded bytes so there's no serialization layer between logic and
socket."

---

## 2. Lists + blocking

**Stages:** create/append/prepend list → LRANGE (pos + neg indexes) → LLEN → LPOP
(with count) → BLPOP → BLPOP with timeout.

**What:** `ListStore` is `Map<String, Deque<String>>` behind a `synchronized`
lock. `BLPOP` blocks the calling thread on `lock.wait()` until an `RPUSH`/`LPUSH`
calls `lock.notifyAll()`, or the timeout elapses.

**Why wait/notify, not a poll loop:** busy-waiting burns a core per blocked
client. `wait()`/`notifyAll()` parks the thread; the pushing thread wakes it. The
lock that guards the map *is* the monitor, so there's no separate condition
object to keep in sync.

**Tricky bits:**
- Negative indexes in `LRANGE` (`-1` = last) need clamping: `start < 0 → max(0,
  size + start)`, and an empty result when `start > stop`.
- Timeout `0` means "block forever" — `wait(0)` already means that in Java, so no
  special case.
- Spurious wakeups: the wait is in a `while` loop re-checking the condition, not
  an `if`.

**Soundbite:** "BLPOP parks the client thread on the same monitor that guards the
list map; a push notifies. No polling, no busy-wait."

---

## 3. Streams

**Stages:** TYPE → XADD → entry-ID validation → partial (`<ms>-*`) and full (`*`)
auto-IDs → XRANGE → `-`/`+` bounds → XREAD (multi-stream) → blocking XREAD →
blocking without timeout → blocking with `$`.

**What:** `StreamStore` keeps entries per key as an ordered list of
`(id, fields)`, id = `<millis>-<seq>`. `XADD` enforces strictly-increasing IDs.
`XREAD BLOCK` reuses the same wait/notify pattern as `BLPOP`.

**Why:** IDs are two longs, so "strictly increasing" is a lexicographic-ish
compare done numerically (ms first, then seq). `$` in blocking `XREAD` means
"only entries added after this call" — resolved to the current last ID at call
time, before blocking.

**Tricky bits:**
- Partial auto-ID `5-*`: seq is `last.seq + 1` if ms matches the last entry, else
  `0`.
- Full auto-ID `*`: ms = `currentTimeMillis()`, seq relative to any existing
  entry with that same ms.
- `XADD` must reject an ID `<=` the last one with the specific error string the
  tester checks (`ERR The ID specified in XADD is equal or smaller...`).

**Soundbite:** "Stream IDs are `ms-seq`; auto-generation and validation are just
careful comparison of two longs, and blocking XREAD is the BLPOP pattern again."

---

## 4. INCR + transactions

**Stages:** INCR (3 sub-stages) → MULTI queueing → EXEC → DISCARD → multiple
transactions → WATCH → WATCH-inside-MULTI error → track key modifications →
UNWATCH → **WATCH via key versions** (a refactor) → heavy refactor.

**What:** `ClientSession` holds `inMulti`, a `commandQueue`, and
`watchedVersions` (`Map<key, versionSnapshot>`). While `inMulti`, every command
except EXEC/DISCARD/WATCH is appended to the queue and answered `+QUEUED`. `EXEC`
replays the queue by calling `dispatch()` recursively and concatenating replies
behind `*<n>\r\n`.

**WATCH / optimistic locking:** `Store` keeps a monotonic `keyVersions`
`ConcurrentHashMap<String, Long>`; every `set()`/`increment()`/`load()` calls
`touch(key)` which does `merge(key, 1, Long::sum)`. `WATCH k` snapshots
`versionOf(k)`. `EXEC` re-reads each watched version — any mismatch → abort,
return nil array (`*-1\r\n`).

**Why versions instead of the first design (callbacks):** the original WATCH
registered a callback from `Store` back into the watching `ClientHandler`, which
(a) made `Store` depend on `ClientHandler` (circular), and (b) forced a
`volatile` dirty flag. Version counters invert it: the watcher *pulls* a
comparison at EXEC time. The only shared state is a map of longs; no back-
reference, no volatile. This was a deliberate mid-project refactor
([memory: command-dispatch-refactor]).

**Tricky bits:**
- EXEC with a failing command inside doesn't abort the transaction — Redis
  transactions aren't atomic-on-error, they just run the queue. Per-command
  errors go into the reply array.
- WATCH inside MULTI is an error (`-ERR WATCH inside MULTI is not allowed`).
- `EXEC`/`DISCARD` without `MULTI` are errors.

**Soundbite:** "WATCH is optimistic locking via per-key monotonic version
counters — EXEC compares snapshots, no locks and no callback from the store back
to the connection."

---

## 5. The command-dispatch refactor

**Stage:** "Heavy Refactoring" — not a feature, a structural change I chose to do
once the switch got unwieldy (~160 lines).

**Before:** one giant `switch (command)` in the handler.

**After:**
- `Command` — functional interface, `byte[] execute(List<String> args)`.
- `CommandGroup` — base class with `add(name, handler)`; subclasses register
  related commands (`StringCommands`, `ListCommands`, `StreamCommands`,
  `KeyCommands`, `SortedSetCommands`, `ServerCommands`, `ConnectionCommands`,
  `RDBPersistenceCommands`).
- `CommandRegistry` — assembles all groups into one `Map<String, Command>`.
- `CommandDispatcher` — owns the *connection-scoped* verbs (MULTI/EXEC/WATCH/
  SUBSCRIBE/AUTH…) because those need the `ClientSession`; routes everything else
  through the registry.

**Why this split:** a data command (`SET`, `ZADD`) only needs its args and a
store. A connection command (`SUBSCRIBE`, `MULTI`) needs the session. Keeping the
session out of the registry means groups are trivially unit-testable with no
socket. Adding a command = one `add(...)` line in the right group.

**Soundbite:** "Registry of stateless handlers keyed by name; the dispatcher
keeps only the verbs that need per-connection state."

---

## 6. Replication

**Stages:** configure listening port → INFO → initial replid/offset → send
handshake (3 parts) → receive handshake → empty RDB transfer → single-replica
propagation → multi-replica propagation → ACKs with no commands → ACKs with
commands → WAIT with no commands → WAIT with commands.

### Master side
- `INFO replication` → `role:master`, `connected_slaves:N`, `master_replid:...`,
  `master_repl_offset:N` as one bulk string of `\r\n`-joined lines.
- On `PSYNC ? -1`: reply `+FULLRESYNC <replid> 0\r\n` immediately followed by
  `$<len>\r\n<88 bytes of empty RDB>` (**no trailing CRLF** after the payload —
  it's a file transfer, not a bulk string).
- After answering PSYNC, `ClientHandler` registers that socket's `OutputStream`
  as a `Replicas.Handle`. Registration happens *inside* the `synchronized(out)`
  block that wrote the RDB, so no propagated write can slip between the RDB and
  the link going live.
- Every write command (`SET`, `DEL`, `INCR`, `LPUSH`…): after executing locally,
  RESP-encode the original args and `replicas.propagate()` to every handle, plus
  `replication.addReplOffset(bytes.length)`.

### Replica side (`ReplicationClient`, daemon thread)
1. Handshake: `PING` → `REPLCONF listening-port <p>` → `REPLCONF capa psync2` →
   `PSYNC ? -1`.
2. Consume the RDB: read `$<len>`, then exactly `len` bytes.
3. `in.resetCount()` — the replication offset counts **only** the command stream
   after the RDB.
4. Loop: read a command. If it's `REPLCONF GETACK *`, reply `REPLCONF ACK
   <offset>`. Otherwise `dispatcher.dispatch(args, session)` and **discard the
   reply** — a replica never answers the master.

### The offset bug (5 things wrong in my first hand-rolled version)
My first attempt counted bytes manually inside `readLine`/`readCommand`. Bugs:
1. `readLine` never counted the `\r` it consumed.
2. `continue` on a GETACK skipped the offset increment for that frame.
3. Case-sensitive `equals("REPLCONF")` also matched other REPLCONF subcommands.
4. `static` counters — shared across instances, so master+replica in one JVM
   corrupted each other.
5. Sampled the offset at the wrong point.

**Fix:** a `CountingInputStream` (a `FilterInputStream` that counts every byte in
both `read()` overrides). Sample `in.count()` **before** reading the current
command, so `REPLCONF ACK` reports "bytes processed before this GETACK". Tester
sequence: `GETACK` #1 → `ACK 0`; then `PING` (14 bytes) + `GETACK` #1 (37) arrive;
`GETACK` #2 → `ACK 51`.

### WAIT
`WAIT <numreplicas> <timeout>`:
- If `master_repl_offset == 0` (nothing written yet), every replica is trivially
  caught up → return `replicas.count()`.
- Otherwise `Replicas.waitForAcks(target, wanted, timeout)`: propagate `REPLCONF
  GETACK *`, then `ackMonitor.wait(remaining)` in a loop until `wanted` replicas
  report `ackedOffset >= target` or the deadline passes. Return the count reached
  (may be more or fewer than `wanted`).

### Why `ReplicationInfo` is an instance, not static
Original design had static replid/offset. The integration tests run a master and
a replica **in the same JVM** — static state made them collide. One instance per
`RedisServer` fixed it.

**Soundbite:** "Replica tracks a byte offset over the post-RDB command stream via
a counting input stream, sampled before each frame; WAIT propagates GETACK and
blocks on a monitor until enough ACKs clear the target offset."

---

## 7. RDB persistence (config + read)

**Stages:** RDB file config → read a key → read multiple keys (types 3/4/5/6).

**What:**
- `Rdb` holds `dir` and `dbfilename` (flag-aware, defaults `.` and `dump.rdb`).
- `RDBPersistenceCommands` answers `CONFIG GET <param>` from a small map (dir,
  dbfilename, appendonly, appenddirname, appendfilename, appendfsync).
- `RdbReader.loadInto(path, stringStore)` parses just what the challenge needs:
  9-byte header, skip metadata (`FA`)/select-db (`FE`)/resize-db (`FB`), apply
  `FC` (ms expiry) / `FD` (sec expiry), load string values (type `0x00`), stop at
  `FF`.

**Why a partial parser:** the challenge only stores string keys. Length encoding
has 4 cases by the top 2 bits (6-bit inline / 14-bit / 32-bit BE / special); the
"special" case covers int-as-string (`C0`/`C1`/`C2` = 8/16/32-bit LE). No LZF,
no other value types — an `IOException` on any unexpected opcode makes gaps loud
instead of silent.

**Tricky bits:**
- Lengths are big-endian; the integer-string encodings are little-endian. Easy to
  mix up.
- `FC` is 8-byte LE ms; `FD` is 4-byte LE seconds → `*1000`. Both are absolute
  timestamps, applied as-is (an already-expired key is evicted lazily on first
  `GET`).
- `CONFIG GET <param>` — the param name is `args.get(2)` (`CONFIG`, `GET`,
  `<param>`), not `args.get(1)`. Got that wrong first.

**Soundbite:** "A minimal RDB reader — header, the handful of opcodes the spec
uses, string values, expiries — and it throws on anything it doesn't recognize
rather than guessing."

---

## 8. AOF persistence

**Stages:** default AOF options → options from flags → create append-only dir →
create append-only file → create manifest → write single command → write multiple
→ filter write commands → replay single → replay multiple.

**What:** `Aof.open(flags, dir)`:
- Returns `Aof.DISABLED` (a no-op singleton) unless `--appendonly yes`.
- Lays out `<dir>/<appenddirname>/` (default `appendonlydir`). Creates
  `<base>.1.incr.aof` + `<base>.manifest` **only if the manifest is absent** —
  the tester may pre-create them.
- Manifest line: `file appendonly.aof.1.incr.aof seq 1 type i`. Reads the last
  `type i` entry to find the active file.
- `append(bytes)`: `Files.write(file, cmd, CREATE, APPEND[, SYNC])`. With
  `--appendfsync always`, `SYNC` forces bytes to disk **before** `append`
  returns.
- `replay(dispatcher)`: sets a `replaying` flag, reads the active file with
  `RespParser`, dispatches each command with a throwaway session, `finally`
  clears the flag.

**Why the `replaying` flag:** the dispatcher's write path does `aof.append()` +
`replicas.propagate()` after every write command. During replay we're *feeding it
write commands* — without the guard it would re-append every command to the AOF
(doubling it) and try to propagate to replicas that don't exist yet.
`if (WRITE_COMMANDS.contains(name) && !aof.isReplaying())` skips both.

**Why fsync-before-return for `always`:** `appendfsync always` is the durability
promise that an acknowledged write survives a crash. Appending after sending the
reply would break that promise. So: execute → append (SYNC) → then the handler
writes the reply.

**Tricky bits:**
- "Did we create the AOF or only the manifest?" — both, but only when the
  manifest doesn't already exist. If it exists, trust it and just read the active
  file name.
- The no-op `DISABLED` singleton means every call site
  (`append`/`replay`/`isReplaying`) is unconditional — no `if (aofEnabled)`
  scattered around.
- ponytail shortcut noted in the file: opens the file per append. Fine at
  challenge scale; keep a channel open if throughput ever matters.

**Soundbite:** "AOF replays through the same dispatch path used for live writes; a
`replaying` flag suppresses re-append and propagation so replay is idempotent."

---

## 9. Pub/Sub

**Stages:** SUBSCRIBE (multi-channel) → subscribed-mode command restrictions →
PING in subscribed mode → PUBLISH → deliver messages → UNSUBSCRIBE.

**What:**
- `ClientSession` keeps a `LinkedHashSet<String> channels`. `subscribe(ch)` /
  `unsubscribe(ch)` return the new count. `inSubscribedMode()` = set non-empty.
- `PubSub` is server-wide: `Map<String, Set<ClientSession>>` (`ConcurrentHashMap`
  + `ConcurrentHashMap.newKeySet()`). `publish(ch, payload)` builds one
  `*3\r\n$7\r\nmessage\r\n$<ch>\r\n$<payload>\r\n` array and calls
  `subscriber.deliver(message)` for each subscriber, returns the count.
- While in subscribed mode, only SUBSCRIBE/UNSUBSCRIBE/PSUBSCRIBE/PUNSUBSCRIBE/
  PING/QUIT/RESET are allowed — anything else → `-ERR Can't execute '<cmd>'...`.
- `PING` in subscribed mode replies `*2\r\n$4\r\npong\r\n$0\r\n\r\n` (array form),
  not `+PONG`.

**Why `deliver()` under `synchronized(connection)`:** the publish happens on the
*publisher's* thread, writing to the *subscriber's* socket — a cross-thread write.
`ClientHandler` also writes to that socket (for the subscriber's own command
replies) under `synchronized(out)`. Same monitor → the two writers can't
interleave a half-written frame.

**Why `ConcurrentHashMap`:** subscribe/unsubscribe/publish all run on different
client threads against the shared channel map. A plain `HashMap` would corrupt
under concurrent structural modification; `newKeySet()` gives a thread-safe
subscriber set per channel.

**Soundbite:** "PUBLISH writes to each subscriber's socket from the publisher's
thread, guarded by the same monitor the subscriber's own handler uses, so frames
never interleave."

---

## 10. Sorted sets

**Stages:** create → add members → ZRANK → ZRANGE → ZRANGE negative indexes →
ZCARD → ZSCORE → ZREM.

**What:** `SortedSetStore` is `Map<String, Map<String, Double>>` (member → score)
behind a lock. Reads that need order (`ZRANGE`, `ZRANK`) sort on the fly by
**(score asc, then member asc lexicographically)**.

**Why sort-on-read, not a sorted structure:** a `TreeSet` keyed on `(score,
member)` would be the "proper" answer, but then score *updates* (re-adding a
member with a new score) mean remove+reinsert, and you're maintaining two
structures. At challenge scale, `O(n log n)` on each ordered read is nothing.
ponytail comment in the file marks the upgrade path.

**Tricky bits:**
- **Comparator type inference:** `Comparator.comparingDouble(set::get)
  .thenComparing(Comparator.naturalOrder())` doesn't compile — `T` infers as
  `Object`. Fix: `Comparator.<String>comparingDouble(set::get)
  .thenComparing(s -> s)`.
- **ZSCORE scientific notation:** `Double.toString(3663832614298053.0)` →
  `"3.6633826142980530E15"`. `formatScore()` prints whole numbers via
  `Long.toString((long) score)` so geohash scores come out as plain integers.
- Negative-index clamping in `ZRANGE`, same logic as `LRANGE`.

**Soundbite:** "Sorted sets are a map per key, ordered on read by (score, member);
a TreeSet is the upgrade if reads ever dominate."

---

## 11. Bitmaps

**Stages:** SETBIT → GETBIT → read string as bits → read bits as string → grow a
bitmap → STRLEN → BITCOUNT → BITOP AND → BITOP OR.

**What:** bitmaps *are* strings. `Store` treats the value as bytes via
ISO-8859-1 (a 1:1 char↔byte map, so every byte round-trips). Bit `offset 0` =
**MSB of byte 0**: `byteIndex = offset / 8`, `mask = 1 << (7 - offset % 8)`.
- `SETBIT` zero-extends with `Arrays.copyOf` if the offset is past the end,
  returns the previous bit.
- `BITCOUNT key [start end]` sums `Integer.bitCount(b & 0xFF)` over a byte range,
  clamping `end` to the last byte.
- `BITOP AND|OR dest src...` — one generic loop, zero-padding shorter sources to
  the longest; result length in bytes is the return value.

**Why ISO-8859-1:** it's the only charset where `new String(bytes,
ISO_8859_1).getBytes(ISO_8859_1)` is guaranteed identity for all 256 byte values.
UTF-8 would mangle bytes ≥ 0x80.

**Tricky bit:** "MSB first" is the opposite of how you'd index a bit in an
integer. `7 - offset % 8` is the whole trick.

**Soundbite:** "Bitmaps reuse the string store via ISO-8859-1; bit 0 is the high
bit of byte 0, so the mask is `1 << (7 - offset%8)`."

---

## 12. Geospatial

**Stages:** GEOADD respond → validate coordinates → store a location → GEOPOS
respond → decode coordinates → GEODIST → GEOSEARCH (search within radius).

**What:** GEO commands are sorted-set commands with a computed score.
`GeoHash.encode(lat, lon)` produces a **52-bit** score: map lat to a 26-bit grid
index over `[-85.05112878, 85.05112878]`, lon to 26 bits over `[-180, 180]`, then
**bit-interleave** (lon in even bits, lat in odd). `decode` reverses it. That's
`GEOADD`'s score; `GEOPOS` decodes it back; `GEODIST` decodes both and runs
haversine with `EARTH_RADIUS_M = 6372797.560856` (Redis's exact constant);
`GEOSEARCH` decodes every member and filters by haversine distance.

**Why 52 bits / this exact scheme:** it's what real Redis does, and the tester
compares scores against real Redis output byte-for-byte. The latitude limit
(85.05°, not 90°) is the Web Mercator clip.

**Tricky bits:**
- Interleave/deinterleave via the classic "magic bit-mask" shifts
  (`0x5555...`, `0x3333...`, `0x0F0F...`). Ported carefully — one wrong mask and
  every score is off.
- `decode` returns `[longitude, latitude]` — that order matters because
  `GEOPOS` returns lon then lat, but `distance()` takes lat first. The array
  index juggling (`a[1], a[0]`) is deliberate.
- **Over-strict test I deleted:** a decode round-trip asserted 1e-6 agreement,
  but a 4-decimal input + 26-bit grid quantization only agrees to ~2.4e-6.
  Replaced with a test against a real full-precision score (what the tester
  actually does).

**Soundbite:** "GEO is sorted sets with a 52-bit interleaved geohash as the
score, matching Redis exactly so the tester's byte comparison passes; distance is
haversine with Redis's earth radius."

---

## 13. ACL + AUTH

**Stages:** ACL WHOAMI → ACL GETUSER → nopass flag → passwords property → ACL
SETUSER (set password) → AUTH command → **enforce authentication**.

**What:**
- `DefaultUser` (one per server): starts `nopass = true`; `ACL SETUSER default
  >pw` stores `SHA-256(pw)` as lowercase hex (`HexFormat.of().formatHex`) and
  clears `nopass`. `authenticates(pw)` = `nopass || hashes.contains(sha256(pw))`.
  All methods `synchronized` — it's shared, mutated by any client thread.
- `ACL WHOAMI` → `default`. `ACL GETUSER default` → `flags` (`nopass` or empty) +
  `passwords` (the hex hashes).
- `AUTH` lives in `CommandDispatcher` (not a command group) because it must call
  `session.authenticate()` — it needs the `ClientSession`.
- **Enforcement:** `ClientSession.authenticated` is seeded from `user.nopass()`
  at `newSession()` time. First line of `dispatch()`:
  `if (!session.isAuthenticated() && !cmd.equals("AUTH")) return "-NOAUTH
  Authentication required."`. So a connection opened *before* a password is set
  stays authenticated for life; one opened *after* must `AUTH` first.

**Why seed from `nopass()` at session creation, not check live:** Redis's rule is
that setting a password doesn't kick existing connections. Snapshotting the auth
requirement when the connection opens gives exactly that behavior for free.

**Tricky bit:** `AUTH default <pw>` has the password at `args.get(2)` (2-arg
`AUTH <pw>` form isn't what the tester sends). Success → `+OK` **simple string**;
failure → `-WRONGPASS ...` verbatim (a raw `simpleError`, not `-ERR ...`).

**Soundbite:** "Auth requirement is snapshotted per connection at open time from
the user's nopass flag, so setting a password locks out new clients without
dropping existing ones — matching Redis."

---

## Cross-cutting: the concurrency model

| State | Thread-safety | Why |
|---|---|---|
| `ClientSession` (MULTI, queue, WATCH, channels, auth) | none needed | only its own handler thread touches it |
| `ListStore`, `StreamStore`, `SortedSetStore` | `synchronized` lock | many client threads read/write |
| `Store.keyVersions` | `ConcurrentHashMap` | WATCH check reads it from the transaction's own thread while others write |
| `Store.data` / `expiry` | **plain HashMap** (a known gap) | not hardened against concurrent string-key writes; challenge tests don't hit it |
| `PubSub.byChannel` | `ConcurrentHashMap` + `newKeySet()` | subscribe/publish race across threads |
| `Replicas.handles` | `CopyOnWriteArrayList` | rare writes (register/drop), frequent iteration (propagate) |
| `ReplicationInfo.replOffset` | `volatile` | written by write-command threads, read by WAIT |

**The one honest gap:** `Store` (string keyspace) uses a plain `HashMap`. Under
heavy concurrent `SET` on the same keys it could corrupt. The challenge's tests
are effectively single-threaded per keyspace, so it's never exercised — but it's
the first thing I'd fix for real use (wrap in `synchronized` like the other
stores, or `ConcurrentHashMap`).

---

## Bugs I hit (quick index)

| Bug | Root cause | Fix |
|---|---|---|
| Replica offset wrong | 5 issues in hand-rolled byte counting (missed `\r`, `continue` skip, case-sensitive match, static counters, wrong sample point) | `CountingInputStream`, sample before each frame |
| `INFO` reply truncated at client | `multiBulkString` emitted repeated `$len\r\ndata\r\n` — invalid | one bulk string of `\r\n`-joined lines |
| `PSYNC` reply `++FULLRESYNC` | `simpleString("+FULLRESYNC…")` double-prefixed | drop the leading `+` |
| master+replica collide in tests | `static` replid/offset | instance `ReplicationInfo` per server |
| AOF doubles on restart | replay feeds write commands back through the write path | `replaying` flag guards append + propagate |
| `CONFIG GET` wrong value | read `args.get(1)` instead of `args.get(2)` | map lookup on `args.get(2)` |
| `SortedSetStore` won't compile | comparator `T` infers as `Object` | `Comparator.<String>comparingDouble(...).thenComparing(s -> s)` |
| `ZSCORE` returns `3.6E15` | `Double.toString` on a large whole number | `formatScore` → `Long.toString((long) score)` |
| GEO decode test flaky | 1e-6 tolerance vs 2.4e-6 quantization error | test against a real full-precision score instead |
| `ReplicationPropagationIT` flaky in CI | race between SET and replica registration | deleted (covered by `CommandDispatcherTest` + `MasterReplicaIT`) |

---

## Test layout

- `*Test` (unit, socket-free): `CommandDispatcherTest` (the big one — dispatch,
  transactions, pub/sub, sorted sets, bitmaps, geo, ACL all without a socket),
  `RespEncoderTest`, `RespParserTest`, `StoreTest`, `SortedSetStoreTest`,
  `ListStoreTest`, `StreamStoreTest`, `GeoHashTest`, `RdbReaderTest`.
- `*IT` (integration, real sockets, extend `RedisServerTestBase`):
  `StringCommandsIT`, `ListCommandsIT`, `StreamCommandsIT`, `KeyCommandsIT`,
  `ConnectionCommandsIT`, `ServerCommandsIT`, `TransactionIT`, `WatchIT`,
  `PubSubDeliveryIT`, `AofDirectoryIT`, `KeyspaceFromRdbIT`,
  `ReplicationHandshakeIT`, `MasterReplicaIT`.
- Surefire runs both `*Test` and `*IT` on `mvn test`.

Split out of a single 1444-line `MainTest` purely for readability — one file per
command category.
