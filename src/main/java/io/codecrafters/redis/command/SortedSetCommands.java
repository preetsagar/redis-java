package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.store.SortedSetStore;

public class SortedSetCommands extends CommandGroup {

    public SortedSetCommands(SortedSetStore store) {
        // ZADD key score member -> number of new members added
        add("ZADD", args -> RespEncoder.respInteger(
                store.add(args.get(1), Double.parseDouble(args.get(2)), args.get(3))));
    }
}