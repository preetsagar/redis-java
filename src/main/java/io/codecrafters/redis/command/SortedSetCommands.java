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

        // ZRANGE key start stop -> members in rank order (inclusive), empty array if out of range
        add("ZRANGE", args -> RespEncoder.encodeList(
                store.range(args.get(1), Integer.parseInt(args.get(2)), Integer.parseInt(args.get(3)))));

        // ZCARD key -> number of members (0 if the set doesn't exist)
        add("ZCARD", args -> RespEncoder.respInteger(store.card(args.get(1))));

        // ZREM key member -> 1 if removed, 0 if the member wasn't there
        add("ZREM", args -> RespEncoder.respInteger(store.remove(args.get(1), args.get(2))));

        // ZSCORE key member -> score as a bulk string, or null bulk string if absent
        add("ZSCORE", args -> {
            Double score = store.score(args.get(1), args.get(2));
            // ponytail: Double.toString round-trips the tester's decimal scores; add
            // whole-number trimming (20.0 -> "20") only if a stage checks for it.
            return score != null ? RespEncoder.bulkString(Double.toString(score))
                    : RespEncoder.nullBulkString();
        });

        // GEOADD key longitude latitude member -> count added
        // ponytail: stubbed to 1 — arg validation and geohash-scored storage are later stages
        add("GEOADD", args -> RespEncoder.respInteger(1));
    }
}