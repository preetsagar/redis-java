package io.codecrafters.redis.command;

import io.codecrafters.redis.ReplicationInfo;
import io.codecrafters.redis.aof.Aof;
import io.codecrafters.redis.client.ClientSession;
import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.pubsub.PubSub;
import io.codecrafters.redis.rdb.Rdb;
import io.codecrafters.redis.replication.Replicas;
import io.codecrafters.redis.store.Database;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

/**
 * Routes a parsed command for one connection: handles the transaction control
 * verbs (MULTI/EXEC/DISCARD/WATCH/UNWATCH) and MULTI queueing itself, and
 * delegates every data command to the {@link CommandRegistry}.
 */
public class CommandDispatcher {

    // Commands that mutate the dataset and must be propagated to replicas.
    private static final Set<String> WRITE_COMMANDS =
            Set.of("SET", "DEL", "INCR", "LPUSH", "RPUSH", "LPOP", "XADD");

    // The only commands a client in subscribed mode may run.
    private static final Set<String> SUBSCRIBED_MODE_ALLOWED = Set.of(
            "SUBSCRIBE", "UNSUBSCRIBE", "PSUBSCRIBE", "PUNSUBSCRIBE", "PING", "QUIT", "RESET");

    private final Database db;
    private final CommandRegistry registry;
    private final Replicas replicas;
    private final ReplicationInfo replication;
    private final Rdb redisDataBase;
    private final Aof aof;
    private final PubSub pubSub;

    public CommandDispatcher(Database db, ReplicationInfo replication, Replicas replicas,
                             Rdb redisDataBase, Aof aof, PubSub pubSub) {
        this.db = db;
        this.replicas = replicas;
        this.replication = replication;
        this.redisDataBase = redisDataBase;
        this.aof = aof;
        this.pubSub = pubSub;
        this.registry = new CommandRegistry(db, replication, replicas, redisDataBase);
    }

    /** A fresh session for a newly connected client; {@code connection} is where
     *  PUBLISH pushes messages (null for socket-free callers). */
    public ClientSession newSession(java.io.OutputStream connection) {
        return new ClientSession(db.stringStore(), connection);
    }

    public ClientSession newSession() {
        return newSession(null);
    }

    public Replicas replicas() {
        return replicas;
    }

    public byte[] dispatch(List<String> args, ClientSession session) {
        String commandName = args.get(0).toUpperCase();

        if (session.inSubscribedMode() && !SUBSCRIBED_MODE_ALLOWED.contains(commandName)) {
            return RespEncoder.error("Can't execute '" + commandName.toLowerCase()
                    + "': only (P|S)SUBSCRIBE / (P|S)UNSUBSCRIBE / PING / QUIT / RESET are allowed in this context");
        }

        if (session.inMulti() && !bypassesQueue(commandName)) {
            session.queue(args);
            return RespEncoder.simpleString("QUEUED");
        }

        return switch (commandName) {
            case "MULTI" -> {
                session.beginMulti();
                yield RespEncoder.simpleString("OK");
            }
            case "EXEC" -> exec(session);
            case "DISCARD" -> discard(session);
            case "WATCH" -> watch(args, session);
            case "UNWATCH" -> {
                session.clearWatches();
                yield RespEncoder.simpleString("OK");
            }
            case "SUBSCRIBE" -> {
                String channel = args.get(1);
                pubSub.subscribe(channel, session);
                yield RespEncoder.concat("*3\r\n".getBytes(),
                        RespEncoder.bulkString("subscribe"),
                        RespEncoder.bulkString(channel),
                        RespEncoder.respInteger(session.subscribe(channel)));
            }
            case "PUBLISH" -> RespEncoder.respInteger(pubSub.publish(args.get(1), args.get(2)));
            case "PING" -> session.inSubscribedMode()
                    ? RespEncoder.concat("*2\r\n".getBytes(),
                            RespEncoder.bulkString("pong"), RespEncoder.bulkString(""))
                    : registry.get("PING").execute(args);
            default -> {
                Command command = registry.get(commandName);
                if (command == null) {
                    yield RespEncoder.error("unknown command '" + args.get(0) + "'");
                }
                byte[] reply = command.execute(args);
                if (WRITE_COMMANDS.contains(commandName) && !aof.isReplaying()) {
                    byte[] writeCommand = RespEncoder.encodeList(args);
                    aof.append(writeCommand); // before the reply — appendfsync always must be durable first
                    replicas.propagate(writeCommand);
                    replication.addReplOffset(writeCommand.length);
                }
                yield reply;
            }
        };
    }

    // Commands that must run immediately even while a MULTI is open.
    private static boolean bypassesQueue(String commandName) {
        return commandName.equals("EXEC") || commandName.equals("DISCARD") || commandName.equals("WATCH");
    }

    private byte[] exec(ClientSession session) {
        if (!session.inMulti()) {
            return RespEncoder.error("EXEC without MULTI");
        }
        session.endMulti();

        if (session.isAnyWatchedKeyDirty()) {
            session.clearQueue();
            session.clearWatches();
            return RespEncoder.emptyList();
        }

        List<List<String>> queued = session.drainQueue();
        ByteArrayOutputStream results = new ByteArrayOutputStream();
        results.writeBytes(("*" + queued.size() + "\r\n").getBytes());
        for (List<String> command : queued) {
            results.writeBytes(dispatch(command, session));
        }
        session.clearWatches();
        return results.toByteArray();
    }

    private byte[] discard(ClientSession session) {
        if (!session.inMulti()) {
            return RespEncoder.error("DISCARD without MULTI");
        }
        session.endMulti();
        session.clearQueue();
        session.clearWatches();
        return RespEncoder.simpleString("OK");
    }

    private byte[] watch(List<String> args, ClientSession session) {
        if (session.inMulti()) {
            return RespEncoder.error("WATCH inside MULTI is not allowed");
        }
        for (String key : args.subList(1, args.size())) {
            session.watch(key);
        }
        return RespEncoder.simpleString("OK");
    }
}