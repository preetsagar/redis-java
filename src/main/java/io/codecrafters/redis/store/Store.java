package io.codecrafters.redis.store;

import java.util.HashMap;

public class Store {

    private final HashMap<String, String> data = new HashMap<>();
    private final HashMap<String, Long> expiry = new HashMap<>();

    public void set(String key, String value) {
        data.put(key, value);
        expiry.remove(key);
    }

    public void set(String key, String value, long ttlMillis) {
        data.put(key, value);
        expiry.put(key, System.currentTimeMillis() + ttlMillis);
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
}