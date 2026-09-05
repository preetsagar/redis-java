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
}