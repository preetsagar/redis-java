package io.codecrafters.redis.store;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class StreamStore {

    private final Map<String, List<StreamEntry>> data = new HashMap<>();

    public String xadd(String key, String id, List<String> fields) {
        data.computeIfAbsent(key, k -> new ArrayList<>()).add(new StreamEntry(id, new ArrayList<>(fields)));
        return id;
    }

    public boolean hasKey(String key) {
        return data.containsKey(key);
    }

    public record StreamEntry(String id, List<String> fields) {}
}