package io.codecrafters.redis.command;

import io.codecrafters.redis.client.ClientSession;
import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.store.Database;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Routes a parsed command for one connection: handles the transaction control
 * verbs (MULTI/EXEC/DISCARD/WATCH/UNWATCH) and MULTI queueing itself, and
 * delegates every data command to the {@link CommandRegistry}.
 */
public class CommandDispatcher {

    private final Database db;
    private final CommandRegistry registry;

    public CommandDispatcher(Database db) {
        this.db = db;
        this.registry = new CommandRegistry(db);
    }

    /** A fresh session for a newly connected client. */
    public ClientSession newSession() {
        return new ClientSession(db.stringStore());
    }

    public byte[] dispatch(List<String> args, ClientSession session) {
        String commandName = args.get(0).toUpperCase();

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
            default -> {
                Command command = registry.get(commandName);
                yield command != null
                        ? command.execute(args)
                        : RespEncoder.error("unknown command '" + args.get(0) + "'");
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