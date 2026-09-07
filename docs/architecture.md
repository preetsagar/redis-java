# Architecture — after the command-dispatch refactor

## The one-paragraph version

`RedisServer` builds **one** `Database` (the three keyspaces) and **one**
`CommandDispatcher` (which builds **one** `CommandRegistry`). Every accepted
socket gets its own thread running a `ClientHandler`, and each `ClientHandler`
owns **one** `ClientSession` (its MULTI / queue / WATCH state). The handler is a
thin loop: `RespParser` turns bytes into `List<String>`, `dispatcher.dispatch()`
turns that into a `byte[]` reply, the handler writes it back. The dispatcher
handles the transaction verbs itself and forwards everything else to a `Command`
looked up by name in the registry.

## Who owns what

| Object | Lifetime | Count | Holds |
|---|---|---|---|
| `Database` | server | 1 | `Store`, `ListStore`, `StreamStore` |
| `CommandDispatcher` | server | 1 | `Database`, `CommandRegistry` |
| `CommandRegistry` | server | 1 | `Map<String, Command>` (name → handler) |
| `ClientHandler` | connection | N (1 thread each) | socket, dispatcher ref, its session |
| `ClientSession` | connection | N | `inMulti`, `commandQueue`, `watchedVersions` |

Shared, mutated by many threads: the three stores. They serialize internally
(`ListStore` / `StreamStore` use a `synchronized` lock; `Store.keyVersions` is a
`ConcurrentHashMap`). `ClientSession` is touched by only its own thread, so it
needs no locking.

## Request flow (normal command)

```mermaid
sequenceDiagram
    actor Client
    participant CH as ClientHandler
    participant RP as RespParser
    participant CD as CommandDispatcher
    participant CR as CommandRegistry
    participant SC as StringCommands
    participant St as Store

    Client->>CH: RESP frame for SET foo bar
    CH->>RP: readCommand()
    RP-->>CH: list [SET, foo, bar]
    CH->>CD: dispatch(args, session)
    Note over CD: name = SET<br/>session.inMulti() is false, so not queued
    CD->>CR: get(SET)
    CR-->>CD: Command
    CD->>SC: execute([SET, foo, bar])
    SC->>St: set(foo, bar)
    Note over St: data.put(...)<br/>touch(foo) bumps keyVersions
    SC-->>CD: +OK
    CD-->>CH: +OK
    CH->>Client: +OK
```

## Transaction flow (MULTI / EXEC / WATCH)

The dispatcher special-cases five verbs *before* touching the registry:

- **MULTI** → `session.beginMulti()`
- **WATCH k...** → for each key `session.watch(k)`, which snapshots
  `Store.versionOf(k)` into `watchedVersions`
- **UNWATCH** → `session.clearWatches()`
- **DISCARD** → drop queue + watches, leave MULTI
- **EXEC** →
  1. not in MULTI → `-ERR EXEC without MULTI`
  2. `session.isAnyWatchedKeyDirty()` — any watched key whose current
     `Store.versionOf()` ≠ the snapshot → clear everything, return `*-1\r\n` (nil)
  3. otherwise: `drainQueue()`, and for each queued command call
     `dispatch()` **recursively**, concatenating the replies behind `*<n>\r\n`

While `inMulti` is true, any command that is *not* EXEC / DISCARD / WATCH is
appended to the queue and answered with `+QUEUED`.

```mermaid
sequenceDiagram
    actor A as Client A
    actor B as Client B
    participant CD as Dispatcher
    participant SA as Session A
    participant St as Store

    Note over A,St: WATCH
    A->>CD: WATCH foo
    CD->>SA: watch(foo)
    SA->>St: versionOf(foo) returns 7
    SA->>SA: watchedVersions = {foo=7}
    CD-->>A: +OK

    Note over A,St: MULTI and queue
    A->>CD: MULTI
    CD->>SA: beginMulti()
    CD-->>A: +OK
    A->>CD: INCR foo
    CD->>SA: queue([INCR, foo])
    CD-->>A: +QUEUED

    Note over B,St: another client mutates the watched key
    B->>CD: SET foo 99
    CD->>St: set(foo, 99)
    Note over St: touch(foo) makes keyVersions[foo] = 8

    Note over A,St: EXEC, optimistic-lock check
    A->>CD: EXEC
    CD->>SA: endMulti()
    CD->>SA: isAnyWatchedKeyDirty?
    SA->>St: versionOf(foo) returns 8
    SA->>SA: 8 != snapshot 7, so DIRTY
    SA-->>CD: true
    CD->>SA: clearQueue and clearWatches
    CD-->>A: nil array (transaction aborted)

    Note over CD,St: if not dirty: drainQueue, then dispatch each<br/>queued command, concatenating the replies
```

## Why WATCH works across threads without callbacks

`Store` keeps a monotonic counter per key:

```
ConcurrentHashMap<String, Long> keyVersions;
touch(key)  -> keyVersions.merge(key, 1L, Long::sum);   // called by every set()/increment()
versionOf(key) -> keyVersions.getOrDefault(key, 0L);
```

`WATCH` records `(key -> version)` on the watching connection. `EXEC` re-reads
`versionOf` and compares. The only cross-thread state is a concurrent map of
longs — no `volatile` flag, and `Store` never holds a reference back to a
`ClientHandler`. That circular dependency (and the callback that forced the
`volatile`) is gone.

## Adding a command later

1. Pick the group (`StringCommands`, `ListCommands`, …) or add a new
   `CommandGroup` subclass.
2. `add("NAME", args -> { ... return RespEncoder.xxx(...); });`
3. If it's a new group, `register(new XxxCommands(...))` in `CommandRegistry`.

Connection-scoped commands (SUBSCRIBE, transaction verbs, replication
handshake) instead go in `CommandDispatcher`, where the `ClientSession` is in
scope.

## Diagrams

The two sequence diagrams above are Mermaid — they render inline on GitHub and in
most Markdown viewers, no tooling needed.

The class diagram is PlantUML in [`classes.puml`](classes.puml) (Graphviz-free —
it uses `!pragma layout smetana`). Render it with:

```
plantuml docs/classes.puml      # produces docs/classes.png
```

or paste it into <https://www.plantuml.com/plantuml>.