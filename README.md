[![progress-banner](https://backend.codecrafters.io/progress/redis/43868fbd-f911-4354-9691-23e2e80422f2)](https://app.codecrafters.io/users/preetsagar?r=2qF)

# Redis Server — Java Implementation

A Redis-compatible server built from scratch in Java: the RESP protocol, 50+
commands, master–replica replication, RDB + append-only-file persistence,
pub/sub, transactions with optimistic locking, sorted sets, bitmaps, geospatial
queries, and ACL authentication.

Built as part of the [CodeCrafters "Build Your Own Redis" Challenge](https://codecrafters.io/challenges/redis).

- **[`docs/revision.md`](docs/revision.md)** — subsystem-by-subsystem walkthrough of what was built and why (bugs, trade-offs)
- **[`docs/interview-prep.md`](docs/interview-prep.md)** — drill sheet: the pitch, per-subsystem soundbites, likely deep-dive Q&A
- **[`docs/architecture.md`](docs/architecture.md)** — component ownership, threading, sequence diagrams

---

## Features

### Protocol
- Full RESP2 (Redis Serialization Protocol) parser and encoder implemented from scratch
- Supports simple strings, bulk strings, integers, arrays, errors, and null responses

### Commands Supported

| Category      | Commands |
|---------------|----------|
| Basic         | `PING`, `ECHO` |
| Strings       | `SET` (EX/PX), `GET`, `INCR` |
| Keys          | `TYPE`, `KEYS` |
| Lists         | `RPUSH`, `LPUSH`, `LRANGE`, `LLEN`, `LPOP` (with count), `BLPOP` |
| Streams       | `XADD`, `XRANGE`, `XREAD` (multi-stream, `BLOCK`, `$`) |
| Transactions  | `MULTI`, `EXEC`, `DISCARD`, `WATCH`, `UNWATCH` |
| Sorted sets   | `ZADD`, `ZRANK`, `ZRANGE`, `ZCARD`, `ZSCORE`, `ZREM` |
| Geospatial    | `GEOADD`, `GEOPOS`, `GEODIST`, `GEOSEARCH` |
| Bitmaps       | `SETBIT`, `GETBIT`, `BITCOUNT`, `BITOP` (AND/OR), `STRLEN` |
| Pub/Sub       | `SUBSCRIBE`, `UNSUBSCRIBE`, `PUBLISH` |
| Replication   | `REPLCONF`, `PSYNC`, `WAIT`, `INFO replication` |
| Persistence   | `CONFIG GET` |
| ACL / Auth    | `AUTH`, `ACL WHOAMI`, `ACL GETUSER`, `ACL SETUSER` |

### Highlights

- **Replication** — full `PING`/`REPLCONF`/`PSYNC` handshake, `+FULLRESYNC` + empty-RDB transfer, live command propagation to replicas, and `WAIT` that blocks on a monitor until enough replicas ACK a target offset. Replica-side offset is tracked byte-for-byte over the post-RDB command stream via a counting input stream
- **Persistence** — loads string keys (with expiries) from an RDB file on startup; append-only-file with manifest handling, `appendfsync always` (fsync before ack), and replay-on-startup that reuses the live dispatch path under a `replaying` flag so it stays idempotent
- **Pub/Sub** — cross-thread message delivery: `PUBLISH` writes to each subscriber's socket from the publisher's thread, guarded by the same monitor the subscriber's own handler uses; subscribed-mode command restrictions
- **Optimistic locking** — `WATCH` snapshots a monotonic per-key version counter; `EXEC` aborts (returns nil) if any watched key changed. No callbacks or locks — the check reads a `ConcurrentHashMap` on the transaction's own thread
- **Blocking operations** — `BLPOP` and `XREAD BLOCK` use `wait()`/`notifyAll()` on the monitor that guards the store — no busy-wait
- **Geospatial** — 52-bit interleaved geohash as the sorted-set score (matches real Redis byte-for-byte), haversine distance with Redis's earth-radius constant
- **ACL auth** — SHA-256 password hashes; the per-connection auth requirement is snapshotted at connect time from the user's `nopass` flag, so setting a password locks out new clients without dropping existing ones
- **Command dispatch** — stateless handlers registered by name in a `CommandRegistry` (one `CommandGroup` per category); `CommandDispatcher` owns only the connection-scoped verbs (`MULTI`/`EXEC`/`WATCH`/`SUBSCRIBE`/`AUTH`…)
- **Concurrency** — thread-per-connection; per-connection state needs no locking, shared stores serialize internally

---

## Architecture

```
src/main/java/io/codecrafters/redis/
├── Main.java                   # Entry point; arg parsing
├── RedisServer.java            # Builds shared services, loads RDB, replays AOF, accept loop
├── ReplicationInfo.java        # role / replid / master_repl_offset (one per server)
├── DefaultUser.java            # ACL: nopass flag + SHA-256 password hashes
├── client/
│   ├── ClientHandler.java      # Per-connection read → dispatch → write loop
│   └── ClientSession.java      # Per-connection state: MULTI, queue, WATCH, channels, auth
├── command/
│   ├── Command.java            # byte[] execute(List<String> args)
│   ├── CommandGroup.java       # Base class: add(name, handler)
│   ├── CommandRegistry.java    # name → Command lookup, assembled from the groups
│   ├── CommandDispatcher.java  # connection-scoped verbs (MULTI/EXEC/WATCH/SUBSCRIBE/AUTH) + routing + write propagation
│   ├── ConnectionCommands.java # PING, ECHO
│   ├── StringCommands.java     # SET, GET, INCR, SETBIT, GETBIT, STRLEN, BITCOUNT, BITOP
│   ├── ListCommands.java       # RPUSH, LPUSH, LRANGE, LLEN, LPOP, BLPOP
│   ├── StreamCommands.java     # XADD, XRANGE, XREAD
│   ├── SortedSetCommands.java  # ZADD/ZRANK/ZRANGE/ZCARD/ZSCORE/ZREM + GEOADD/GEOPOS/GEODIST/GEOSEARCH
│   ├── GeoHash.java            # 52-bit interleaved geohash encode/decode + haversine
│   ├── KeyCommands.java        # TYPE, KEYS
│   ├── ServerCommands.java     # INFO, REPLCONF, PSYNC, WAIT, ACL
│   └── RDBPersistenceCommands.java  # CONFIG GET
├── protocol/
│   ├── RespParser.java         # Parses incoming RESP commands
│   └── RespEncoder.java        # Encodes every RESP reply type to bytes
├── store/
│   ├── Database.java           # Bundles the four keyspaces behind one handle
│   ├── Store.java              # Strings + bitmaps; TTL + per-key version counters
│   ├── ListStore.java          # Lists with blocking pop
│   ├── StreamStore.java        # Streams: ID validation + blocking read
│   └── SortedSetStore.java     # member → score, ordered on read
├── replication/
│   ├── ReplicationClient.java  # Replica side: handshake, RDB, apply master's stream (daemon thread)
│   └── Replicas.java           # Master side: replica links, propagate(), waitForAcks()
├── pubsub/
│   └── PubSub.java             # channel → subscribers; publish()
├── rdb/
│   ├── Rdb.java                # dir / dbfilename config; empty-RDB bytes
│   └── RdbReader.java          # Minimal RDB file parser (startup load)
└── aof/
    └── Aof.java                # Append-only file: open/append/replay, manifest handling
```

More detail — component ownership, threading table, and sequence diagrams for a
plain command, `WATCH`/`MULTI`/`EXEC`, and replication — is in
[`docs/architecture.md`](docs/architecture.md). The build-order walkthrough with
rationale and bugs is in [`docs/revision.md`](docs/revision.md).

---

## Running Locally

**Prerequisites:** Java 17+, Maven

```sh
# Run the server (listens on port 6379)
./your_program.sh

# With flags
./your_program.sh --port 6380
./your_program.sh --replicaof "localhost 6379"                       # run as a replica
./your_program.sh --dir /tmp/redis --dbfilename dump.rdb             # load an RDB on startup
./your_program.sh --appendonly yes --appendfsync always             # append-only-file persistence
```

Open a second terminal and send commands using `nc` (netcat — available on macOS by default):

```sh
# PING
echo -e "*1\r\n\$4\r\nPING\r\n" | nc -G 1 localhost 6379

# SET foo bar
echo -e "*3\r\n\$3\r\nSET\r\n\$3\r\nfoo\r\n\$3\r\nbar\r\n" | nc -G 1 localhost 6379

# GET foo
echo -e "*2\r\n\$3\r\nGET\r\n\$3\r\nfoo\r\n" | nc -G 1 localhost 6379

# INCR counter
echo -e "*2\r\n\$4\r\nINCR\r\n\$7\r\ncounter\r\n" | nc -G 1 localhost 6379
```

The server logs each request and response to stdout:

```
[CONNECTED  127.0.0.1:52341]
[REQUEST  127.0.0.1:52341] → [SET, foo, bar]
[RESPONSE 127.0.0.1:52341] ← +OK\r\n
[DISCONNECTED] 127.0.0.1:52341
```

If you have `redis-cli` installed (`brew install redis`), you can use it instead:

```sh
redis-cli PING
redis-cli SET foo bar
redis-cli GET foo
```

## Running Tests

```sh
mvn test
```

Tests are split into:
- **Unit tests** (`*Test`, socket-free) — `CommandDispatcherTest` (the big one: dispatch, transactions, pub/sub, sorted sets, bitmaps, geo, ACL — all without a socket), plus `RespParserTest`, `RespEncoderTest`, `StoreTest`, `ListStoreTest`, `StreamStoreTest`, `SortedSetStoreTest`, `GeoHashTest`, `RdbReaderTest`
- **Integration tests** (`*IT`, real TCP) — one class per area, each extending `RedisServerTestBase`: `ConnectionCommandsIT`, `StringCommandsIT`, `ListCommandsIT`, `StreamCommandsIT`, `KeyCommandsIT`, `ServerCommandsIT`, `TransactionIT`, `WatchIT`, `PubSubDeliveryIT`, `AofDirectoryIT`, `KeyspaceFromRdbIT`, `ReplicationHandshakeIT`, `MasterReplicaIT`

Surefire is configured to run both `*Test` and `*IT` in `mvn test`.

---
