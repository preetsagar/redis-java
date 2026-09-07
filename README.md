[![progress-banner](https://backend.codecrafters.io/progress/redis/43868fbd-f911-4354-9691-23e2e80422f2)](https://app.codecrafters.io/users/preetsagar?r=2qF)

# Redis Server — Java Implementation

A Redis-compatible server built from scratch in Java, implementing the RESP (Redis Serialization Protocol) and 20+ Redis commands including blocking operations, streams, and transactions.

Built as part of the [CodeCrafters "Build Your Own Redis" Challenge](https://codecrafters.io/challenges/redis).

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
| Keys          | `TYPE` |
| Lists         | `RPUSH`, `LPUSH`, `LRANGE`, `LLEN`, `LPOP` (with count), `BLPOP` |
| Streams       | `XADD`, `XRANGE`, `XREAD` |
| Transactions  | `MULTI`, `EXEC`, `DISCARD`, `WATCH`, `UNWATCH` |

### Highlights

- **Blocking operations** — `BLPOP` and `XREAD BLOCK` use `wait()`/`notifyAll()` to block the client thread until data arrives or timeout expires, without busy-waiting
- **Redis Streams** — `XADD` validates entry IDs (must be strictly increasing), supports explicit IDs, partial wildcards (`<ms>-*`), and full auto-generation (`*`); `XRANGE` supports `-`/`+` bounds; `XREAD` supports multiple streams and blocking with `$`
- **Transactions** — `MULTI`/`EXEC` queues commands per-connection, executes them atomically on `EXEC`; errors in individual commands don't abort the transaction; `DISCARD` clears the queue
- **Optimistic locking** — `WATCH` snapshots a monotonic per-key version counter; `EXEC` aborts (returns nil) if any watched key was modified by another connection since the `WATCH`. No callbacks or locks — the check reads a `ConcurrentHashMap` on the transaction's own thread
- **Command dispatch** — commands are registered by name into a `CommandRegistry` (one `CommandGroup` per category); `CommandDispatcher` owns the connection-scoped verbs (`MULTI`/`EXEC`/`WATCH`/…) and routes everything else through the registry
- **TTL support** — `SET` with `EX` (seconds) or `PX` (milliseconds); expired keys are lazily evicted on `GET`
- **Thread-per-client concurrency** — each client connection runs in its own thread; shared stores are protected with `synchronized` monitors

---

## Architecture

```
src/main/java/io/codecrafters/redis/
├── Main.java                   # Entry point
├── RedisServer.java            # ServerSocket accept loop; builds Database + CommandDispatcher
├── client/
│   ├── ClientHandler.java      # Per-connection read → dispatch → write loop
│   └── ClientSession.java      # Per-connection state: MULTI flag, queued commands, WATCH snapshots
├── command/
│   ├── Command.java            # byte[] execute(List<String> args)
│   ├── CommandGroup.java       # Base class for a group of related command handlers
│   ├── CommandRegistry.java    # name → Command lookup, assembled from the groups
│   ├── CommandDispatcher.java  # MULTI/EXEC/DISCARD/WATCH/UNWATCH + queueing; routes the rest
│   ├── ConnectionCommands.java # PING, ECHO
│   ├── StringCommands.java     # SET, GET, INCR
│   ├── ListCommands.java       # RPUSH, LPUSH, LRANGE, LLEN, LPOP, BLPOP
│   ├── StreamCommands.java     # XADD, XRANGE, XREAD
│   └── KeyCommands.java        # TYPE
├── protocol/
│   ├── RespParser.java         # Parses incoming RESP commands
│   └── RespEncoder.java        # Encodes responses to RESP bytes
└── store/
    ├── Database.java           # Bundles the three keyspaces behind one handle
    ├── Store.java              # String key-value store with TTL + per-key version counters
    ├── ListStore.java          # List store with blocking pop support
    └── StreamStore.java        # Stream store with ID validation and blocking read
```

More detail — including sequence diagrams for a plain command and for `WATCH`/`MULTI`/`EXEC` — is in [`docs/architecture.md`](docs/architecture.md).

---

## Running Locally

**Prerequisites:** Java 17+, Maven

```sh
# Run the server (listens on port 6379)
./your_program.sh
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
- **Unit tests** (`*Test`) — `StoreTest`, `ListStoreTest`, `StreamStoreTest`, `RespParserTest`, `RespEncoderTest`, and `CommandDispatcherTest` (drives the dispatcher directly, no sockets — covers the MULTI/EXEC/WATCH logic)
- **Integration tests** (`*IT`) — one class per command category (`ConnectionCommandsIT`, `StringCommandsIT`, `ListCommandsIT`, `StreamCommandsIT`, `KeyCommandsIT`, `TransactionIT`, `WatchIT`), each extending `RedisServerTestBase`, which starts a real server on port 6379 and talks to it over TCP

Surefire is configured to run both `*Test` and `*IT` in `mvn test`.

---
