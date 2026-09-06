package io.codecrafters.redis.store;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Store {

    private final HashMap<String, String> data = new HashMap<>();
    private final HashMap<String, Long> expiry = new HashMap<>();

    // Monotonic modification counter per key. WATCH snapshots these; EXEC compares.
    private final ConcurrentHashMap<String, Long> keyVersions = new ConcurrentHashMap<>();

    private void touch(String key) {
        keyVersions.merge(key, 1L, Long::sum);
    }

    /** Current modification version of a key (0 if it has never been modified). */
    public long versionOf(String key) {
        return keyVersions.getOrDefault(key, 0L);
    }

    public void set(String key, String value) {
        data.put(key, value);
        expiry.remove(key);
        touch(key);
    }

    public void set(String key, String value, long ttlMillis) {
        data.put(key, value);
        expiry.put(key, System.currentTimeMillis() + ttlMillis);
        touch(key);
    }

    // Returns null if key doesn't exist or has expired.
    public String get(String key) {
        if (expiry.containsKey(key) && System.currentTimeMillis() >= expiry.get(key)) {
            data.remove(key);
            expiry.remove(key);
            return null;
        }
        return data.get(key);
    }

    public String increment(String key) {
        String existing = get(key);
        int current = existing == null ? 0 : Integer.parseInt(existing);
        current++;
        set(key, String.valueOf(current));
        return String.valueOf(current);
    }

}