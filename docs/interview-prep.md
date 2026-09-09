# Interview drill sheet — Redis in Java

Fast recall before an interview. Deeper "what & why" is in
[`revision.md`](revision.md); component diagrams in
[`architecture.md`](architecture.md). This page is the stuff you should be able
to say out loud without notes.

---

## The 60-second pitch

> I built a Redis-compatible server from scratch in Java — the RESP wire
> protocol, ~50 commands, and the harder subsystems: master–replica replication,
> RDB and append-only-file persistence, pub/sub, transactions with optimistic
> locking, sorted sets, bitmaps, geospatial queries, and ACL auth. It's
> thread-per-connection. The core design decision is a split between *stateless
> command handlers* registered by name in a registry, and a *dispatcher* that
> owns the handful of verbs needing per-connection state — transactions,
> subscriptions, auth. That split is why the command layer is unit-testable
> without a socket. It was the CodeCrafters challenge, so the stages told me
> *what* to build; the architecture, concurrency model, and test strategy were
> mine.

## The 10-second version

> A Redis server in Java: RESP protocol, replication, RDB+AOF persistence,
> pub/sub, transactions, sorted sets / bitmaps / geo, ACL. Thread-per-connection,
> registry-based command dispatch.

---

## Architecture in three sentences

1. `RedisServer` builds **one** of every shared thing — the keyspaces, the
   dispatcher, the replica registry, the pub/sub hub, the user table — then runs
   an accept loop.
2. Each connection gets **its own thread** (`ClientHandler`) and **its own
   session object** (`ClientSession`) holding transaction / subscription / auth
   state.
3. The handler is a thin loop: parse bytes → `dispatch()` → write reply bytes;
   the dispatcher routes data commands to a name-keyed registry and handles the
   connection-scoped verbs itself.

---

## Per-subsystem soundbites (say one line, expand if asked)

| Subsystem | One line |
|---|---|
| **RESP** | Length-prefixed, so the parser is ~40 lines; every handler returns raw encoded bytes, no serialization layer. |
| **Blocking (BLPOP / XREAD)** | Client thread parks on `wait()` on the same monitor that guards the store; a push calls `notifyAll()`. No busy-wait. |
| **Transactions** | `MULTI` queues per-connection, `EXEC` replays the queue by recursing through `dispatch()`. Per-command errors don't abort — Redis transactions aren't atomic-on-error. |
| **WATCH** | Optimistic locking via a monotonic per-key version counter in a `ConcurrentHashMap`. `EXEC` compares snapshots; mismatch → abort with nil. No locks, no callback from store to connection. |
| **Command dispatch** | Registry of stateless handlers keyed by name; dispatcher keeps only verbs that need the session. |
| **Replication (master)** | On `PSYNC`: `+FULLRESYNC` + empty-RDB transfer, then register the socket and propagate every write command's raw bytes to it. |
| **Replication (replica)** | Daemon thread: handshake, consume RDB, then apply the master's command stream, discarding replies. |
| **Replication offset** | Bytes read off the post-RDB stream, counted by a `FilterInputStream`, sampled *before* each frame so `REPLCONF ACK` reports "bytes before this GETACK". |
| **WAIT** | Propagate `REPLCONF GETACK *`, then block on a monitor until N replicas report an acked offset ≥ the target, or timeout. |
| **RDB** | Minimal reader: header + the ~5 opcodes the spec uses + string values + expiries. Throws on anything unrecognized rather than guessing. |
| **AOF** | Write commands RESP-appended after execution (fsync-before-return for `appendfsync always`). Replay feeds the file back through `dispatch()` under a `replaying` flag that suppresses re-append + propagation. |
| **Pub/Sub** | `PUBLISH` writes to each subscriber's socket *from the publisher's thread*, guarded by the same monitor the subscriber's own handler uses, so frames never interleave. |
| **Sorted sets** | Map of member→score per key, sorted on read by (score, then member). TreeSet is the upgrade if reads dominate. |
| **Bitmaps** | They *are* strings, accessed as bytes via ISO-8859-1 (only charset that round-trips all 256 values). Bit 0 = MSB of byte 0, so mask = `1 << (7 - offset%8)`. |
| **Geospatial** | Sorted sets with a 52-bit interleaved geohash as the score — matches real Redis byte-for-byte. Distance is haversine with Redis's exact earth radius. |
| **ACL / AUTH** | SHA-256 password hashes; per-connection auth requirement is *snapshotted at connect time* from the user's `nopass` flag, so setting a password locks out new clients without dropping existing ones. |

---

## The concurrency story (they will ask)

**Model:** one thread per client connection. Chosen for a simple mental model;
the challenge never pushes connection counts where thread-per-connection hurts
(the alternative — an event loop / NIO selector like real Redis — is more code
and only pays off at thousands of idle connections).

**Three tiers of state:**

1. **Per-connection** (`ClientSession`: in-transaction flag, queued commands,
   watched versions, subscribed channels, authenticated flag) — touched by
   exactly one thread, so **zero synchronization**.
2. **Shared keyspaces** (`ListStore`, `StreamStore`, `SortedSetStore`) —
   `synchronized` on an internal lock; that same lock is the monitor blocking
   commands wait on.
3. **Shared coordination state** — `ConcurrentHashMap` for pub/sub channels and
   for `Store.keyVersions` (WATCH reads it from the transaction's own thread
   while other threads write); `CopyOnWriteArrayList` for replica handles (rare
   writes, frequent iteration); `volatile` for the replication offset.

**Socket writes are serialized:** a single connection's `OutputStream` can be
written by three things — its own handler's replies, a `PUBLISH` from another
thread, a replica propagation from another thread. All go through
`synchronized(out)`, so a frame is never half-written.

**The one honest gap:** `Store` (the string keyspace) uses a plain `HashMap`, not
`ConcurrentHashMap` or a lock. Under heavy concurrent `SET` on the same keys it
could corrupt. The challenge's tests are effectively single-threaded per
keyspace so it's never hit — but it's the first thing I'd fix for real use.
*(Volunteer this. It shows you know where the bodies are buried.)*

---

## Quiz yourself

Cover the answers below and work through these out loud. If one doesn't come,
reload that section of [`revision.md`](revision.md). (Ask Claude "quiz me with the
deep-dive questions one at a time" to run it interactively.)

1. Walk me through what happens when a client sends `SET foo bar`.
2. How does WATCH detect a conflict without locking?
3. Why does the replica track a byte offset, and what's the subtle part?
4. How is the AOF replay idempotent?
5. Why put AUTH in the dispatcher instead of a command handler?
6. Pub/sub — how does a message get from PUBLISH to a subscriber on another connection?
7. What would you change / what are the limitations?
8. How did you test it?
9. Why thread-per-connection and not an event loop? When would that choice break?
10. A client runs `MULTI`, then `INCR x`, then `EXEC`. Another client `SET x foo` lands between the `MULTI` and the `EXEC`. What happens, and why?
11. Why ISO-8859-1 for bitmaps specifically, and not UTF-8 or ASCII?
12. The master sends two writes then `REPLCONF GETACK *`. What offset does the replica report, and how does it know that number?

Extra credit: what were the *five* bugs in the first hand-rolled replication
offset counter?

---

## Likely deep-dive questions + answers

**Q: Walk me through what happens when a client sends `SET foo bar`.**
Handler reads the RESP array into `["SET","foo","bar"]`, calls
`dispatch(args, session)`. Dispatcher checks: authenticated? not in subscribed
mode? not inside an open MULTI (else queue it)? Then it looks up `SET` in the
registry, runs the handler which does `store.set("foo","bar")` — that also bumps
`keyVersions["foo"]` for any watcher. Since `SET` is a write and we're not
replaying the AOF, the dispatcher then RESP-encodes the args, appends them to the
AOF, propagates them to every replica, and adds the byte count to the
replication offset. Returns `+OK`; handler writes it under `synchronized(out)`.

**Q: How does WATCH detect a conflict without locking?**
Every key has a monotonic version counter in a `ConcurrentHashMap`. Any write
increments it. `WATCH k` copies the current version into the session. `EXEC`
re-reads each watched key's version and compares to the snapshot — any difference
means someone wrote it since the WATCH, so abort and return nil. The transaction
thread only ever *reads* the concurrent map, writers only *increment*, so there's
no lost update and no lock. My first design had the store call back into the
connection to set a `volatile` dirty flag — that made the store depend on the
connection class (circular) and needed the volatile. Version counters invert the
dependency.

**Q: Why does the replica track a byte offset, and what's the subtle part?**
The master and replica both count bytes of the replication command stream so
`WAIT` can know how caught-up each replica is. The subtle part: when the master
sends `REPLCONF GETACK *`, the replica must answer with the offset of bytes
processed *before* that GETACK — the GETACK itself doesn't count until it's been
processed. So I sample the byte counter *before* reading each frame. My first
hand-rolled byte counting had five separate bugs (missed the `\r`, a `continue`
that skipped the increment, a case-sensitive command match, static counters
shared between the master and replica running in one JVM, wrong sample point). I
replaced it with a `FilterInputStream` that counts every byte at the source.

**Q: How is the AOF replay idempotent?**
Replay reads the file and feeds each command back through the normal `dispatch()`
path — same code that handles live writes. But the live write path also *appends
to the AOF and propagates to replicas*. During replay that would double the file
and crash on absent replicas. So there's a `replaying` boolean; the write path is
`if (isWrite(cmd) && !aof.isReplaying())`. Replay sets it, `finally` clears it.

**Q: Why put AUTH in the dispatcher instead of a command handler?**
Command handlers are stateless — they get args and a store, nothing else. `AUTH`
has to flip the *connection's* authenticated flag, which lives in the session.
The dispatcher is the only place with both the command and the session in scope.
Same reason `MULTI`, `SUBSCRIBE`, and the replication verbs live there.

**Q: Pub/sub — how does a message get from PUBLISH to a subscriber on another
connection?**
`PUBLISH` runs on the publishing client's thread. The pub/sub hub has a
`channel → Set<ClientSession>` map. For each subscriber it calls
`session.deliver(bytes)`, which writes to *that subscriber's* socket
`OutputStream`. That's a cross-thread write, so it's under `synchronized(conn)` —
the exact monitor the subscriber's own `ClientHandler` uses for its replies. So
even if the subscriber is mid-reply, the message frame won't interleave.

**Q: What would you change / what are the limitations?**
Plain `HashMap` in the string store (mentioned above). Thread-per-connection
instead of an event loop. AOF opens the file per append instead of holding a
channel. RDB is read-only — I never implemented `SAVE`/`BGSAVE`. Sorted sets sort
on every read instead of maintaining a sorted structure. All are fine at
challenge scale and all have a clear upgrade path; none are correctness bugs.

**Q: How did you test it?**
Two layers. Socket-free unit tests drive the dispatcher directly with
`List<String>` in, `byte[]` out — that covers transactions, pub/sub, sorted sets,
bitmaps, geo, ACL without any networking. Then integration tests that boot a real
server on a port and talk RESP over TCP, one class per area, for the things that
only matter end-to-end: the replication handshake, master→replica propagation,
AOF file layout, RDB load, cross-connection pub/sub delivery.

**Q: Why thread-per-connection and not an event loop? When would that choice
break?**
It's the simplest model that's correct: blocking reads, blocking writes, one
thread owns a connection's state so most of it needs no synchronization. Real
Redis is single-threaded with an event loop (epoll/kqueue) because at tens of
thousands of mostly-idle connections, one OS thread per connection is real memory
(~1 MB stack each) and scheduler overhead. My design would break there — the fix
is a `Selector` and non-blocking channels, which also means rewriting the
blocking commands (`BLPOP`, `XREAD BLOCK`) as registered continuations instead of
parked threads. At challenge scale (tens of connections) none of that matters.

**Q: `MULTI`, `INCR x`, then another client does `SET x foo` before `EXEC`. What
happens?**
The transaction runs and `INCR x` executes against whatever `x` is at `EXEC`
time — so it either increments `"foo"` (fails, `-ERR value is not an integer`,
and that error goes *into the reply array* without aborting the rest) or
whatever. Redis transactions are **not** isolated from other clients' writes;
`MULTI` only batches, it doesn't lock. If I wanted the transaction to *notice*
that write and bail, I'd `WATCH x` before the `MULTI` — then the version-counter
check at `EXEC` sees `x` changed and aborts with nil. That's the whole point of
WATCH: `MULTI` alone gives you batching, `WATCH`+`MULTI` gives you
compare-and-swap.

**Q: Why ISO-8859-1 for bitmaps and not UTF-8 or ASCII?**
Bitmaps are stored in the same string keyspace as everything else, so I need to
go `String ↔ byte[]` losslessly for all 256 byte values. ISO-8859-1 (Latin-1) is
the one charset where byte `n` maps to code point `n` and back for every `n` in
0–255. ASCII only covers 0–127. UTF-8 mangles anything ≥ 0x80 into multi-byte
sequences, so `new String(bytes, UTF_8).getBytes(UTF_8)` isn't identity — a
`SETBIT` at a high offset would corrupt the value.

**Q: Master sends two writes then `REPLCONF GETACK *`. What offset does the
replica report and how does it know?**
It reports the total bytes of those two write commands — everything on the
replication stream *before* the GETACK frame, not including the GETACK itself. It
knows because a `CountingInputStream` (a `FilterInputStream`) increments a counter
on every byte read from the master socket, and I sample that counter *before*
reading each frame. So when I've read the two writes and I'm about to read the
GETACK, the counter is exactly "bytes of the two writes", and that's what goes in
the `REPLCONF ACK`. The counter is reset to 0 right after the initial RDB, so it
only ever measures the post-RDB command stream.

**Extra credit — the five bugs in the first offset counter:**
1. `readLine` consumed the trailing `\r` but never counted it.
2. The `continue` after handling a GETACK skipped the code that added that
   frame's bytes to the offset.
3. `args.get(0).equals("REPLCONF")` was case-sensitive and also matched other
   REPLCONF subcommands I didn't mean to special-case.
4. The byte counters were `static`, so a master and a replica running in the same
   JVM (which the integration tests do) corrupted each other's counts.
5. I sampled the offset *after* reading the command instead of before, so ACKs
   were off by one frame.
The fix collapsed all five: count every byte at the source in a
`FilterInputStream`, sample before each frame, instance state not static.

---

## Numbers / constants worth remembering

- RESP: `+` simple string, `$` bulk string, `:` integer, `*` array, `-` error;
  `$-1` null bulk, `*-1` null array.
- Empty RDB transfer: `$<len>\r\n<bytes>` with **no trailing CRLF** (file, not
  bulk string).
- Geohash score: **52 bits** = 26-bit lat grid + 26-bit lon grid, interleaved.
- Latitude limit for geo: **±85.05112878°** (Web Mercator clip, not ±90).
- Earth radius Redis uses: **6372797.560856 m**.
- Bit offset 0 = **most significant bit of byte 0**.
- AOF manifest line: `file appendonly.aof.1.incr.aof seq 1 type i`.

---

## STAR framing (behavioural round)

- **Situation:** wanted a systems project with real depth, not a CRUD app.
- **Task:** implement enough of Redis that a compatibility test suite passes —
  protocol, persistence, replication, the data types.
- **Action:** built it in Java over ~110 incremental stages; did two deliberate
  mid-project refactors (command dispatch into a registry; WATCH from callbacks
  to version counters) once the original shape started to hurt; kept a two-layer
  test suite.
- **Result:** all stages pass; ~30 source files, clean package boundaries,
  documented architecture. Comfortable explaining any subsystem end to end.
- **Learned:** where a "simple" approach (hand-rolled byte counting, callback-
  based change detection) quietly accumulates bugs, and that inverting a
  dependency is often cheaper than synchronizing around it.
