package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.store.SortedSetStore;

public class SortedSetCommands extends CommandGroup {

    public SortedSetCommands(SortedSetStore store) {
        // ZADD key score member -> number of new members added
        add("ZADD", args -> RespEncoder.respInteger(
                store.add(args.get(1), Double.parseDouble(args.get(2)), args.get(3))));

        // ZRANK key member -> 0-based rank, or null bulk string if key/member absent
        add("ZRANK", args -> {
            Integer rank = store.rank(args.get(1), args.get(2));
            return rank != null ? RespEncoder.respInteger(rank) : RespEncoder.nullBulkString();
        });
    }
}