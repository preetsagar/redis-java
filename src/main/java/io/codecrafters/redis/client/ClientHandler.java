package io.codecrafters.redis.client;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.protocol.RespParser;
import io.codecrafters.redis.store.ListStore;
import io.codecrafters.redis.store.Store;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket client;
    private final Store store;
    private final ListStore listStore;

    public ClientHandler(Socket client, Store store, ListStore listStore) {
        this.client = client;
        this.store = store;
        this.listStore = listStore;
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
            case "LLEN" -> {
                out.write(RespEncoder.RespInteger(listStore.dataSize(args.get(1))));
            }
            default -> out.write(RespEncoder.error("unknown command '" + args.get(0) + "'"));
        }
    }
}