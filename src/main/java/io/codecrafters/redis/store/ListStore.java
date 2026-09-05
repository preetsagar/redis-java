package io.codecrafters.redis.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ListStore {

    private final HashMap<String, List<String>> data = new HashMap<>();
    private final Object lock = new Object();

    public int rightPush(String key, List<String> values) {
        synchronized (lock) {
            List<String> list = data.computeIfAbsent(key, k -> new ArrayList<>());
            list.addAll(values);
            lock.notifyAll();
            return list.size();
        }
    }

    public int leftPush(String key, List<String> values) {
        synchronized (lock) {
            List<String> list = data.computeIfAbsent(key, k -> new ArrayList<>());
            List<String> reversed = new ArrayList<>(values);
            Collections.reverse(reversed);
            list.addAll(0, reversed);
            lock.notifyAll();
            return list.size();
        }
    }

    public List<String> lRange(String key, int start, int end) {
        synchronized (lock) {
            List<String> list = data.getOrDefault(key, new ArrayList<>());
            int size = list.size();
            if (start < 0) start = Math.max(0, size + start);
            if (end < 0) end = size + end;
            end = Math.min(end, size - 1);
            if (start > end) return new ArrayList<>();
            return new ArrayList<>(list.subList(start, end + 1));
        }
    }

    public int dataSize(String key) {
        synchronized (lock) {
            List<String> list = data.get(key);
            return list == null ? 0 : list.size();
        }
    }

    public String leftPop(String key) {
        synchronized (lock) {
            List<String> list = data.get(key);
            if (list == null || list.isEmpty()) return null;
            return list.remove(0);
        }
    }

    public List<String> leftPop(String key, int count) {
        synchronized (lock) {
            List<String> list = data.get(key);
            if (list == null || list.isEmpty()) return new ArrayList<>();
            int toRemove = Math.min(count, list.size());
            List<String> removed = new ArrayList<>(list.subList(0, toRemove));
            list.subList(0, toRemove).clear();
            return removed;
        }
    }

    // Blocks until an element is available or timeout expires.
    // timeoutSeconds == 0 means block indefinitely.
    // Returns [key, value] or empty list on timeout.
    public List<String> blockedLeftPop(String key, long timeoutMilliSeconds) throws InterruptedException {
        long deadlineMillis = timeoutMilliSeconds > 0
                ? System.currentTimeMillis() + timeoutMilliSeconds
                : Long.MAX_VALUE;

        synchronized (lock) {
            while (true) {
                List<String> list = data.get(key);
                if (list != null && !list.isEmpty()) {
                    return List.of(key, list.remove(0));
                }
                long remaining = deadlineMillis - System.currentTimeMillis();
                if (timeoutMilliSeconds > 0 && remaining <= 0) return Collections.emptyList();
                lock.wait(timeoutMilliSeconds == 0 ? 0 : remaining);
            }
        }
    }
}
