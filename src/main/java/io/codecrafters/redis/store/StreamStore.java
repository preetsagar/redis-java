package io.codecrafters.redis.store;

import java.util.*;
import java.util.stream.Collectors;

public class StreamStore {

    public record StreamEntry(String id, List<String> fields) {}
    private final Map<String, List<StreamEntry>> data = new HashMap<>();
    private final Map<String, String> lastIds = new HashMap<>();

    private final Object lock = new Object();

    // Returns the inserted ID, or throws IllegalArgumentException with the Redis error message.
    public String xadd(String key, String id, List<String> fields) {
        synchronized (lock) {
            String lastId = lastIds.getOrDefault(key, "0-0");
            if (id.equals("0-0")) {
                throw new IllegalArgumentException("The ID specified in XADD must be greater than 0-0");
            }
            if (isWildCardID(id)) {
                id = generateId(id, lastId);
            } else if (!isGreaterThan(id, lastId)) {
                throw new IllegalArgumentException("The ID specified in XADD is equal or smaller than the target stream top item");
            }
            data.computeIfAbsent(key, k -> new ArrayList<>()).add(new StreamEntry(id, new ArrayList<>(fields)));
            lastIds.put(key, id);
            lock.notifyAll();
            return id;
        }
    }

    public boolean hasKey(String key) {
        synchronized (lock) {
            return data.containsKey(key);
        }
    }

    // Returns entries strictly greater than afterId (exclusive).
    // Blocks up to milliseconds waiting for new entries. 0 = infinite blocking.
    public List<StreamEntry> xread(String key, String afterId, long milliseconds) throws InterruptedException {
        synchronized (lock) {
            long deadline = milliseconds > 0 ? System.currentTimeMillis() + milliseconds : 0;
            while (true) {
                long[] after = parseId(afterId);
                List<StreamEntry> result = data.getOrDefault(key, Collections.emptyList()).stream()
                        .filter(e -> compareIds(parseId(e.id()), after) > 0)
                        .collect(Collectors.toList());
                if (!result.isEmpty()) return result;
                long remaining = deadline > 0 ? deadline - System.currentTimeMillis() : 0;
                if (deadline > 0 && remaining <= 0) return result;
                lock.wait(remaining);
            }
        }
    }

    public List<StreamEntry> xrange(String key, String startId, String endId) {
        synchronized (lock) {
            List<StreamEntry> entries = data.getOrDefault(key, new ArrayList<>());
            if (startId.equals("-")) startId = "0-0";
            if (endId.equals("+")) endId = Long.MAX_VALUE + "-" + Long.MAX_VALUE;
            long[] start = parseRangeId(startId, 0L);
            long[] end = parseRangeId(endId, Long.MAX_VALUE);
            return entries.stream()
                    .filter(e -> compareIds(parseId(e.id()), start) >= 0
                            && compareIds(parseId(e.id()), end) <= 0)
                    .collect(Collectors.toList());
        }
    }

    private long[] parseRangeId(String id, long defaultSeq) {
        if (id.contains("-")) {
            String[] parts = id.split("-");
            return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
        }
        return new long[]{Long.parseLong(id), defaultSeq};
    }

    private long[] parseId(String id) {
        String[] parts = id.split("-");
        return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
    }

    private int compareIds(long[] a, long[] b) {
        if (a[0] != b[0]) return Long.compare(a[0], b[0]);
        return Long.compare(a[1], b[1]);
    }

    private boolean isGreaterThan(String id, String lastId) {
        String[] last = lastId.split("-");
        String[] current = id.split("-");
        long lastMillis = Long.parseLong(last[0]);
        long lastSeq = Long.parseLong(last[1]);
        long currMillis = Long.parseLong(current[0]);
        long currSeq = Long.parseLong(current[1]);
        return currMillis > lastMillis || (currMillis == lastMillis && currSeq > lastSeq);
    }

    private boolean isWildCardID(String id) {
        return id.contains("*");
    }

    private String generateId(String id, String lastId) {
        String[] lastParts = lastId.split("-");
        long lastMillis = Long.parseLong(lastParts[0]);
        long lastSeq = Long.parseLong(lastParts[1]);

        if (id.equals("*")) {
            long currMillis = System.currentTimeMillis();
            long seq = (currMillis == lastMillis) ? lastSeq + 1 : 0;
            return currMillis + "-" + seq;
        }

        // Format: "<millis>-*"
        long currMillis = Long.parseLong(id.split("-")[0]);
        long seq = (currMillis == lastMillis) ? lastSeq + 1 : 0;
        return currMillis + "-" + seq;
    }
}