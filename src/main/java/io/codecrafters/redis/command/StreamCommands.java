package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.store.StreamStore;
import io.codecrafters.redis.store.StreamStore.StreamEntry;

import java.util.ArrayList;
import java.util.List;

public class StreamCommands extends CommandGroup {

    public StreamCommands(StreamStore streamStore) {
        add("XADD", args -> {
            try {
                String id = streamStore.xadd(args.get(1), args.get(2), args.subList(3, args.size()));
                return RespEncoder.bulkString(id);
            } catch (IllegalArgumentException e) {
                return RespEncoder.error(e.getMessage());
            }
        });

        add("XRANGE", args -> {
            List<StreamEntry> entries = streamStore.xrange(args.get(1), args.get(2), args.get(3));
            return RespEncoder.encodeStreamEntries(entries);
        });

        add("XREAD", args -> {
            try {
                // Syntax A: XREAD STREAMS key1 ... id1 ...
                // Syntax B: XREAD BLOCK <ms> STREAMS key1 ... id1 ...
                boolean hasBlock = args.get(1).equalsIgnoreCase("BLOCK");
                long blockMs = hasBlock ? Long.parseLong(args.get(2)) : 10L;
                int streamsIdx = hasBlock ? 4 : 2; // index of first key after STREAMS
                int streamCount = (args.size() - streamsIdx) / 2;
                List<String> keys = args.subList(streamsIdx, streamsIdx + streamCount);
                List<String> ids = args.subList(streamsIdx + streamCount, args.size());
                List<List<StreamEntry>> allEntries = new ArrayList<>();
                for (int i = 0; i < streamCount; i++) {
                    allEntries.add(streamStore.xread(keys.get(i), ids.get(i), blockMs));
                }
                allEntries = allEntries.stream().filter(e -> !e.isEmpty()).toList();
                if (allEntries.isEmpty()) {
                    return RespEncoder.emptyList();
                }
                return RespEncoder.encodeXRead(keys, allEntries);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return RespEncoder.emptyList();
            }
        });
    }
}