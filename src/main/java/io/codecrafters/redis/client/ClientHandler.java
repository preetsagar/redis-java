package io.codecrafters.redis.client;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.protocol.RespParser;
import io.codecrafters.redis.store.ListStore;
import io.codecrafters.redis.store.Store;
import io.codecrafters.redis.store.StreamStore;
import io.codecrafters.redis.store.StreamStore.StreamEntry;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket client;
    private final Store store;
    private final ListStore listStore;
    private final StreamStore streamStore;

    public ClientHandler(Socket client, Store store, ListStore listStore, StreamStore streamStore) {
        this.client = client;
        this.store = store;
        this.listStore = listStore;
        this.streamStore = streamStore;
    }

    @Override
    public void run() {
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             OutputStream out = client.getOutputStream()) {
            RespParser parser = new RespParser(in);
            List<String> args;
            while ((args = parser.readCommand()) != null) {
                handleCommand(args, out);
            }
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    private void handleCommand(List<String> args, OutputStream out) throws IOException {
        switch (args.get(0).toUpperCase()) {
            case "PING" -> out.write(RespEncoder.simpleString("PONG"));
            case "ECHO" -> out.write(RespEncoder.bulkString(args.get(1)));
            case "SET" -> {
                if (args.size() > 3 && args.get(3).equalsIgnoreCase("EX")) {
                    store.set(args.get(1), args.get(2), Long.parseLong(args.get(4)) * 1000);
                } else if (args.size() > 3 && args.get(3).equalsIgnoreCase("PX")) {
                    store.set(args.get(1), args.get(2), Long.parseLong(args.get(4)));
                } else {
                    store.set(args.get(1), args.get(2));
                }
                out.write(RespEncoder.simpleString("OK"));
            }
            case "GET" -> {
                String value = store.get(args.get(1));
                out.write(value != null ? RespEncoder.bulkString(value) : RespEncoder.nullBulkString());
            }
            case "RPUSH" -> out.write(RespEncoder.RespInteger(listStore.rightPush(args.get(1), args.subList(2, args.size()))));
            case "LPUSH" -> out.write(RespEncoder.RespInteger(listStore.leftPush(args.get(1), args.subList(2, args.size()))));
            case "LRANGE" -> {
                List<String> result = listStore.lRange(args.get(1), Integer.parseInt(args.get(2)), Integer.parseInt(args.get(3)));
                out.write(RespEncoder.encodeList(result));
            }
            case "LLEN" -> out.write(RespEncoder.RespInteger(listStore.dataSize(args.get(1))));
            case "LPOP" -> {
                if (args.size() == 2) {
                    String value = listStore.leftPop(args.get(1));
                    out.write(value != null ? RespEncoder.bulkString(value) : RespEncoder.nullBulkString());
                } else {
                    List<String> values = listStore.leftPop(args.get(1), Integer.parseInt(args.get(2)));
                    out.write(RespEncoder.encodeList(values));
                }
            }
            case "BLPOP" -> {
                try {
                    long timeoutMillis = (long)(Double.parseDouble(args.get(2)) * 1000);
                    List<String> result = listStore.blockedLeftPop(args.get(1), timeoutMillis);
                    out.write(result.isEmpty() ? RespEncoder.emptyList() : RespEncoder.encodeList(result));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    out.write(RespEncoder.emptyList());
                }
            }
            case "XRANGE" -> {
                List<StreamEntry> entries = streamStore.xrange(args.get(1), args.get(2), args.get(3));
                out.write(RespEncoder.encodeStreamEntries(entries));
            }
            case "XADD" -> {
                try {
                    String id = streamStore.xadd(args.get(1), args.get(2), args.subList(3, args.size()));
                    out.write(RespEncoder.bulkString(id));
                } catch (IllegalArgumentException e) {
                    out.write(RespEncoder.error(e.getMessage()));
                }
            }
            case "TYPE" -> {
                String key = args.get(1);
                String type;
                if (store.get(key) != null) {
                    type = "string";
                } else if (listStore.dataSize(key) > 0) {
                    type = "list";
                } else if (streamStore.hasKey(key)) {
                    type = "stream";
                } else {
                    type = "none";
                }
                out.write(RespEncoder.simpleString(type));
            }
            default -> out.write(RespEncoder.error("unknown command '" + args.get(0) + "'"));
        }
    }
}