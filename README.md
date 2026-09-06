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
| Strings       | `SET` (EX/PX), `GET`, `INCR`, `TYPE` |
| Lists         | `RPUSH`, `LPUSH`, `LRANGE`, `LLEN`, `LPOP` (with count), `BLPOP` |
| Streams       | `XADD`, `XRANGE`, `XREAD` |
| Transactions  | `MULTI`, `EXEC`, `DISCARD` |

### Highlights

- **Blocking operations** — `BLPOP` and `XREAD BLOCK` use `wait()`/`notifyAll()` to block the client thread until data arrives or timeout expires, without busy-waiting
- **Redis Streams** — `XADD` validates entry IDs (must be strictly increasing), supports explicit IDs, partial wildcards (`<ms>-*`), and full auto-generation (`*`); `XRANGE` supports `-`/`+` bounds; `XREAD` supports multiple streams and blocking with `$`
- **Transactions** — `MULTI`/`EXEC` queues commands per-connection, executes them atomically on `EXEC`; errors in individual commands don't abort the transaction; `DISCARD` clears the queue
- **TTL support** — `SET` with `EX` (seconds) or `PX` (milliseconds); expired keys are lazily evicted on `GET`
- **Thread-per-client concurrency** — each client connection runs in its own thread; shared stores are protected with `synchronized` monitors

---

## Architecture

```
src/main/java/io/codecrafters/redis/
├── Main.java                   # Entry point
├── RedisServer.java            # ServerSocket accept loop
├── client/
│   └── ClientHandler.java      # Per-connection command routing + MULTI/EXEC state
├── protocol/
│   ├── RespParser.java         # Parses incoming RESP commands
│   └── RespEncoder.java        # Encodes responses to RESP bytes
└── store/
    ├── Store.java              # String key-value store with TTL
    ├── ListStore.java          # List store with blocking pop support
    └── StreamStore.java        # Stream store with ID validation and blocking read
```

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
- **Unit tests** — `StoreTest`, `ListStoreTest`, `StreamStoreTest`, `RespParserTest`, `RespEncoderTest`
- **Integration tests** — `MainTest` spins up a real server on port 6379 and sends commands over TCP sockets

---
