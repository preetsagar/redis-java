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

    /**
     * Inserts a key straight from an RDB load. {@code expiryAtEpochMillis} is an
     * absolute timestamp (not a TTL), or null for no expiry; an already-elapsed
     * timestamp is kept as-is and evicted lazily on the next {@link #get}.
     */
    public void load(String key, String value, Long expiryAtEpochMillis) {
        data.put(key, value);
        if (expiryAtEpochMillis != null) {
            expiry.put(key, expiryAtEpochMillis);
        } else {
            expiry.remove(key);
        }
        touch(key);
    }

    /** Every key that currently exists and hasn't expired. */
    public Set<String> keys() {
        Set<String> live = new HashSet<>();
        for (String key : new ArrayList<>(data.keySet())) {
            if (get(key) != null) {
                live.add(key);
            }
        }
        return live;
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