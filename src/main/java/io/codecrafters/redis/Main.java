package io.codecrafters.redis;

import java.util.HashMap;
import java.util.Map;

public class Main {

    private static final String DEFAULT_PORT = "6379";
    private static final String DEFAULT_ROLE = "master";
    private static Map<String, String> parsed = new HashMap<>();

    public static void main(String[] args) {
        parse(args);
        new RedisServer(
                Integer.parseInt(parsed.getOrDefault("port", DEFAULT_PORT)),
                parsed.containsKey("replicaof") ? "slave" : "master"
        ).start();
    }

    private static void parse(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            parsed.put(args[i].substring(2).toLowerCase(), args[++i]);
        }
    }
}