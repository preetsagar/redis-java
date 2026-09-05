package io.codecrafters.redis.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ListStore {

    private final HashMap<String, List<String>> data = new HashMap<>();

    public int rpush(String key, List<String> values) {
        List<String> list = data.computeIfAbsent(key, k -> new ArrayList<>());
        list.addAll(values);
        return list.size();
    }

    public List<String> lRange(String key, int start, int end) {
        List<String> list = data.getOrDefault(key, new ArrayList<>());
        int size = list.size();
        if (end < 0) end = size + end;
        end = Math.min(end, size - 1);
        if (start < 0 || start > end) return new ArrayList<>();
        return list.subList(start, end + 1);
    }
}