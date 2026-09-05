package io.codecrafters.redis.client;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.protocol.RespParser;
import io.codecrafters.redis.store.Store;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket client;
    private final Store store;

    public ClientHandler(Socket client, Store store) {
        this.client = client;
        this.store = store;
    }

    @Override
    public void run() {
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             OutputStream out = client.getOutputStream()) {
            RespParser parser = new RespParser(in);
            String[] args;
            while ((args = parser.readCommand()) != null) {
                handleCommand(args, out);
            }
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    private void handleCommand(String[] args, OutputStream out) throws IOException {
        switch (args[0].toUpperCase()) {
            case "PING" -> out.write(RespEncoder.simpleString("PONG"));
            case "ECHO" -> out.write(RespEncoder.bulkString(args[1]));
            case "SET" -> {
                if (args.length > 3 && args[3].equalsIgnoreCase("EX")) {
                    store.set(args[1], args[2], Long.parseLong(args[4]) * 1000);
                } else if (args.length > 3 && args[3].equalsIgnoreCase("PX")) {
                    store.set(args[1], args[2], Long.parseLong(args[4]));
                } else {
                    store.set(args[1], args[2]);
                }
                out.write(RespEncoder.simpleString("OK"));
            }
            case "GET" -> {
                String value = store.get(args[1]);
                out.write(value != null ? RespEncoder.bulkString(value) : RespEncoder.nullBulkString());
            }
            default -> out.write(RespEncoder.error("unknown command '" + args[0] + "'"));
        }
    }
}