package io.codecrafters.redis.store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sorted sets: {@code key -> (member -> score)}.
 *
 * <p>ponytail: plain HashMap per key, sorted on every read. Fine at challenge
 * scale; swap for a TreeSet keyed on (score, member) if read volume grows.
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

    /** Number of members in the sorted set; 0 if the key doesn't exist. */
    public int card(String key) {
        synchronized (lock) {
            Map<String, Double> set = data.get(key);
            return set == null ? 0 : set.size();
        }
    }

    /** Score of {@code member}, or null if the key or member doesn't exist. */
    public Double score(String key, String member) {
        synchronized (lock) {
            Map<String, Double> set = data.get(key);
            return set == null ? null : set.get(member);
        }
    }

    /** 0-based index of {@code member} ordered by (score asc, then member asc); null if absent. */
    public Integer rank(String key, String member) {
        synchronized (lock) {
            Map<String, Double> set = data.get(key);
            if (set == null || !set.containsKey(member)) {
                return null;
            }
            return orderedMembers(set).indexOf(member);
        }
    }

    /**
     * Members from {@code start} to {@code stop} inclusive, in rank order. A
     * negative index counts from the end (-1 = last); if its magnitude exceeds
     * the size it clamps to 0. Empty if the range selects nothing.
     */
    public List<String> range(String key, int start, int stop) {
        synchronized (lock) {
            Map<String, Double> set = data.get(key);
            if (set == null) {
                return List.of();
            }
            List<String> ordered = orderedMembers(set);
            int size = ordered.size();
            if (start < 0) {
                start = Math.max(0, size + start);
            }
            if (stop < 0) {
                stop = Math.max(0, size + stop);
            }
            if (start > stop || start >= size) {
                return List.of();
            }
            return new ArrayList<>(ordered.subList(start, Math.min(stop, size - 1) + 1));
        }
    }

    private static List<String> orderedMembers(Map<String, Double> set) {
        List<String> ordered = new ArrayList<>(set.keySet());
        ordered.sort(Comparator.<String>comparingDouble(set::get).thenComparing(s -> s));
        return ordered;
    }
}