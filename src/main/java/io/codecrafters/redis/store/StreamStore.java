package io.codecrafters.redis.store;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class StreamStore {

    private final Map<String, List<StreamEntry>> data = new HashMap<>();
    private final Map<String, String> lastIds = new HashMap<>();

    // Returns the inserted ID, or throws IllegalArgumentException with the Redis error message.
    public String xadd(String key, String id, List<String> fields) {
        String lastId = lastIds.getOrDefault(key, "0-0");
        if (id.equals("0-0")) {
            throw new IllegalArgumentException("The ID specified in XADD must be greater than 0-0");
        }
        if (!isGreaterThan(id, lastId)) {
            throw new IllegalArgumentException("The ID specified in XADD is equal or smaller than the target stream top item");
        }
        data.computeIfAbsent(key, k -> new ArrayList<>()).add(new StreamEntry(id, new ArrayList<>(fields)));
        lastIds.put(key, id);
        return id;
    }

    public boolean hasKey(String key) {
        return data.containsKey(key);
    }

    public record StreamEntry(String id, List<String> fields) {}

    private boolean isGreaterThan(String id, String lastId) {
        String[] last = lastId.split("-");
        String[] current = id.split("-");
        long lastMillis = Long.parseLong(last[0]);
        long lastSeq = Long.parseLong(last[1]);
        long currMillis = Long.parseLong(current[0]);
        long currSeq = Long.parseLong(current[1]);
        return currMillis > lastMillis || (currMillis == lastMillis && currSeq > lastSeq);
    }
}