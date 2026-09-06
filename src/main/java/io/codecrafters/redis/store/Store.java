package io.codecrafters.redis.store;

import io.codecrafters.redis.client.ClientHandler;

import java.util.*;

public class Store {

    private final HashMap<String, String> data = new HashMap<>();
    private final HashMap<String, Long> expiry = new HashMap<>();

    private final HashMap<String, Set<ClientHandler>> keysBeingWatchedByClient = new HashMap<>();

    public void set(String key, String value) {
        data.put(key, value);
        expiry.remove(key);
        keysBeingWatchedByClient.getOrDefault(key, new HashSet<>())
                .forEach(client -> client.setIsAnyWatchKeyUpdated(true));
    }

    public void set(String key, String value, long ttlMillis) {
        data.put(key, value);
        expiry.put(key, System.currentTimeMillis() + ttlMillis);
        keysBeingWatchedByClient.getOrDefault(key, new HashSet<>())
                .stream()
                .forEach(client -> {
                    client.setIsAnyWatchKeyUpdated(true);
                }
            );
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

    public void addWatcher(String key, ClientHandler clientHandler) {
        keysBeingWatchedByClient.computeIfAbsent(key, k -> new HashSet<>()).add(clientHandler);
    }

    public void removeWatcher(String key, ClientHandler clientHandler) {
        Set<ClientHandler> watcher = keysBeingWatchedByClient.get(key);
        if (watcher != null) watcher.remove(clientHandler);
    }
}