package io.codecrafters.redis.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ListStore {

    private final HashMap<String, List<String>> data = new HashMap<>();

    public int rpush(String key, String value) {
        List<String> list = data.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(value);
        return list.size();
    }
}