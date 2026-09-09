package io.codecrafters.redis.store;

import java.util.HashMap;
import java.util.Map;

/**
 * Sorted sets: {@code key -> (member -> score)}.
 *
 * <p>ponytail: plain HashMap per key — nothing reads members in score order yet.
 * Switch to a score-ordered structure when ZRANGE / ZRANK land.
 */
public class SortedSetStore {

    private final Map<String, Map<String, Double>> data = new HashMap<>();
    private final Object lock = new Object();

    /** Adds or updates {@code member}; returns 1 if it was newly added, 0 if it already existed. */
    public int add(String key, double score, String member) {
        synchronized (lock) {
            Map<String, Double> set = data.computeIfAbsent(key, k -> new HashMap<>());
            boolean isNew = set.put(member, score) == null;
            return isNew ? 1 : 0;
        }
    }
}