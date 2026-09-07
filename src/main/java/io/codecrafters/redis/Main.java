package io.codecrafters.redis;

public class Main {

    private static final int DEFAULT_PORT = 6379;

    public static void main(String[] args) {
        new RedisServer(parsePort(args)).start();
    }

    // Supports: --port <n>  (defaults to 6379)
    private static int parsePort(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals("--port")) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return DEFAULT_PORT;
    }
}