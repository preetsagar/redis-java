package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.store.Database;

import java.util.ArrayList;

public class KeyCommands extends CommandGroup {

    public KeyCommands(Database db) {
        // KEYS <pattern> — only the "*" pattern is supported.
        add("KEYS", args -> RespEncoder.encodeList(new ArrayList<>(db.stringStore().keys())));

        add("TYPE", args -> {
            String key = args.get(1);
            String type;
            if (db.stringStore().get(key) != null) {
                type = "string";
            } else if (db.listStore().dataSize(key) > 0) {
                type = "list";
            } else if (db.streamStore().hasKey(key)) {
                type = "stream";
            } else {
                type = "none";
            }
            return RespEncoder.simpleString(type);
        });
    }
}