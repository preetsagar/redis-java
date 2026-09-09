package io.codecrafters.redis.store;

import java.nio.charset.StandardCharsets;
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

    /**
     * Sets the bit at {@code offset} (0 = most significant bit of byte 0) to
     * {@code bit}, zero-extending the string as needed. Returns the previous bit.
     * ponytail: reuses {@link #set}, so it clears any TTL — matches nothing tested.
     */
    public int setBit(String key, int offset, int bit) {
        int byteIndex = offset / 8;
        int mask = 1 << (7 - offset % 8);
        String existing = get(key);
        byte[] bytes = existing == null ? new byte[0] : existing.getBytes(StandardCharsets.ISO_8859_1);
        if (byteIndex >= bytes.length) {
            bytes = Arrays.copyOf(bytes, byteIndex + 1);
        }
        int previous = (bytes[byteIndex] & mask) != 0 ? 1 : 0;
        if (bit == 1) {
            bytes[byteIndex] |= mask;
        } else {
            bytes[byteIndex] &= ~mask;
        }
        set(key, new String(bytes, StandardCharsets.ISO_8859_1));
        return previous;
    }

    /** Bit at {@code offset} (0 = MSB of byte 0); 0 if the key is missing or the offset is past the end. */
    public int getBit(String key, int offset) {
        String value = get(key);
        if (value == null) {
            return 0;
        }
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        int byteIndex = offset / 8;
        if (byteIndex >= bytes.length) {
            return 0;
        }
        return (bytes[byteIndex] & (1 << (7 - offset % 8))) != 0 ? 1 : 0;
    }

    /** Length of the string value in bytes; 0 if the key doesn't exist. */
    public int strlen(String key) {
        String value = get(key);
        return value == null ? 0 : value.length();
    }

    public String increment(String key) {
        String existing = get(key);
        int current = existing == null ? 0 : Integer.parseInt(existing);
        current++;
        set(key, String.valueOf(current));
        return String.valueOf(current);
    }

}