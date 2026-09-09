package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.store.SortedSetStore;

import java.util.ArrayList;
import java.util.List;

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
            return score != null ? RespEncoder.bulkString(formatScore(score))
                    : RespEncoder.nullBulkString();
        });

        // GEOADD key longitude latitude member -> count added (score = geohash of the coords)
        add("GEOADD", args -> {
            double longitude = Double.parseDouble(args.get(2));
            double latitude = Double.parseDouble(args.get(3));
            if (longitude < -180 || longitude > 180
                    || latitude < -LATITUDE_LIMIT || latitude > LATITUDE_LIMIT) {
                return RespEncoder.error("invalid longitude,latitude pair " + longitude + "," + latitude);
            }
            return RespEncoder.respInteger(
                    store.add(args.get(1), GeoHash.encode(latitude, longitude), args.get(4)));
        });

        // GEOPOS key member... -> per member: [longitude, latitude], or a null array if absent
        // ponytail: coords hardcoded to "0" — decoding the score is a later stage
        add("GEOPOS", args -> {
            List<byte[]> entries = new ArrayList<>();
            for (String member : args.subList(2, args.size())) {
                entries.add(store.score(args.get(1), member) != null
                        ? RespEncoder.array(RespEncoder.bulkString("0"), RespEncoder.bulkString("0"))
                        : RespEncoder.emptyList()); // *-1\r\n
            }
            return RespEncoder.array(entries.toArray(byte[][]::new));
        });
    }

    /** Whole scores print as plain integers (matching Redis / GEO scores); others keep their decimals. */
    private static String formatScore(double score) {
        if (score == Math.rint(score) && !Double.isInfinite(score)) {
            return Long.toString((long) score);
        }
        return Double.toString(score);
    }

    // Web Mercator (EPSG:3857) clips latitude here rather than at +/-90.
    private static final double LATITUDE_LIMIT = 85.05112878;
}