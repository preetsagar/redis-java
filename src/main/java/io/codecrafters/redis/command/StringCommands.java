package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.store.Store;

public class StringCommands extends CommandGroup {

    public StringCommands(Store store) {
        add("SET", args -> {
            if (args.size() > 3 && args.get(3).equalsIgnoreCase("EX")) {
                store.set(args.get(1), args.get(2), Long.parseLong(args.get(4)) * 1000);
            } else if (args.size() > 3 && args.get(3).equalsIgnoreCase("PX")) {
                store.set(args.get(1), args.get(2), Long.parseLong(args.get(4)));
            } else {
                store.set(args.get(1), args.get(2));
            }
            return RespEncoder.simpleString("OK");
        });

        add("GET", args -> {
            String value = store.get(args.get(1));
            return value != null ? RespEncoder.bulkString(value) : RespEncoder.nullBulkString();
        });

        add("INCR", args -> {
            try {
                String value = store.increment(args.get(1));
                return RespEncoder.respInteger(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                return RespEncoder.error("value is not an integer or out of range");
            }
        });
    }
}