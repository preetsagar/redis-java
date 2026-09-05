package io.codecrafters.redis.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ListStore {

    private final HashMap<String, List<String>> data = new HashMap<>();

    public int rightPush(String key, List<String> values) {
        List<String> list = data.computeIfAbsent(key, k -> new ArrayList<>());
        list.addAll(values);
        return list.size();
    }

    public List<String> lRange(String key, int start, int end) {
        List<String> list = data.getOrDefault(key, new ArrayList<>());
        int size = list.size();
        if (start < 0) start = Math.max(0, size + start);
        if (end < 0) end = size + end;
        end = Math.min(end, size - 1);
        if (start > end) return new ArrayList<>();
        return list.subList(start, end + 1);
    }

    public int leftPush(String key, List<String> values) {
        List<String> list = data.computeIfAbsent(key, k -> new ArrayList<>());
        List<String> reversed = new ArrayList<>(values);
        Collections.reverse(reversed);
        list.addAll(0, reversed);
        return list.size();
    }

    public int dataSize(String key) {
        List<String> list = data.get(key);
        return list == null ? 0 : list.size();
    }

    // Returns null if list is empty or doesn't exist.
    public String leftPop(String key) {
        List<String> list = data.get(key);
        if (list == null || list.isEmpty()) return null;
        return list.remove(0);
    }
}