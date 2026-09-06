package io.codecrafters.redis.client;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.protocol.RespParser;
import io.codecrafters.redis.store.ListStore;
import io.codecrafters.redis.store.Store;
import io.codecrafters.redis.store.StreamStore;
import io.codecrafters.redis.store.StreamStore.StreamEntry;

import java.io.*;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClientHandler implements Runnable {

    private final Socket client;
    private final Store store;
    private final ListStore listStore;
    private final StreamStore streamStore;

    private boolean inMulti = false;
    private final List<List<String>> commandQueue = new ArrayList<>();
    private final Set<String> watchingKeys = new HashSet<>();
    private volatile boolean isAnyWatchKeyUpdated = false;

    public void setIsAnyWatchKeyUpdated(boolean flag) {
        this.isAnyWatchKeyUpdated = flag;
    }

    public ClientHandler(Socket client, Store store, ListStore listStore, StreamStore streamStore) {
        this.client = client;
        this.store = store;
        this.listStore = listStore;
        this.streamStore = streamStore;
    }

    @Override
    public void run() {
        String clientInfo = client.getInetAddress().getHostAddress() + ":" + client.getPort();
        System.out.println("[CONNECTED  " + clientInfo + "]");
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             OutputStream out = client.getOutputStream()) {
            RespParser parser = new RespParser(in);
            List<String> args;
            while ((args = parser.readCommand()) != null) {
                System.out.println("[REQUEST  " + clientInfo + "] → " + args);
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                handleCommand(args, buf);
                byte[] response = buf.toByteArray();
                System.out.println("[RESPONSE " + clientInfo + "] ← " + new String(response).replace("\r\n", "\\r\\n"));
                out.write(response);
            }
        } catch (IOException e) {
            System.out.println("[ERROR]    " + clientInfo + " — " + e.getMessage());
        }
        System.out.println("[DISCONNECTED] " + clientInfo);
    }

    private void clearWatches() {
        watchingKeys.forEach(key -> store.removeWatcher(key, this));
        watchingKeys.clear();
        isAnyWatchKeyUpdated = false;
    }

    private void handleCommand(List<String> args, OutputStream out) throws IOException {
        String cmd = args.get(0).toUpperCase();

        // Queue all commands inside MULTI (except EXEC itself)
        if (inMulti && !cmd.equals("EXEC") && !cmd.equals("DISCARD")&& !cmd.equals("WATCH")) {
            commandQueue.add(args);
            out.write(RespEncoder.simpleString("QUEUED"));
            return;
        }

        switch (cmd) {
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
            case "WATCH" -> {
                if(inMulti) {
                    out.write(RespEncoder.error("WATCH inside MULTI is not allowed"));
                }
                else {
                    String key = args.get(1);
                    store.addWatcher(key, this);
                    watchingKeys.add(key);
                    out.write(RespEncoder.simpleString("OK"));
                }
            }
            case "GET" -> {
                String value = store.get(args.get(1));
                out.write(value != null ? RespEncoder.bulkString(value) : RespEncoder.nullBulkString());
            }
            case "INCR" -> {
                try {
                    String value = store.increment(args.get(1));
                    out.write(RespEncoder.respInteger(Integer.parseInt(value)));
                } catch (NumberFormatException e) {
                    out.write(RespEncoder.error("value is not an integer or out of range"));
                }
            }
            case "MULTI" -> {
                inMulti = true;
                out.write(RespEncoder.simpleString("OK"));
            }
            case "EXEC" -> {
                if (!inMulti) {
                    out.write(RespEncoder.error("EXEC without MULTI"));
                } else if (isAnyWatchKeyUpdated) {
                    inMulti = false;
                    commandQueue.clear();
                    clearWatches();
                    out.write(RespEncoder.emptyList());
                } else {
                    inMulti = false;
                    ByteArrayOutputStream results = new ByteArrayOutputStream();
                    results.write(("*" + commandQueue.size() + "\r\n").getBytes());
                    for (List<String> queued : commandQueue) {
                        ByteArrayOutputStream cmdOut = new ByteArrayOutputStream();
                        handleCommand(queued, cmdOut);
                        results.write(cmdOut.toByteArray());
                    }
                    commandQueue.clear();
                    clearWatches();
                    out.write(results.toByteArray());
                }
            }
            case "DISCARD" -> {
                if (!inMulti) {
                    out.write(RespEncoder.error("DISCARD without MULTI"));
                } else {
                    inMulti = false;
                    commandQueue.clear();
                    out.write(RespEncoder.simpleString("OK"));
                }
            }
            case "RPUSH" -> out.write(RespEncoder.respInteger(listStore.rightPush(args.get(1), args.subList(2, args.size()))));
            case "LPUSH" -> out.write(RespEncoder.respInteger(listStore.leftPush(args.get(1), args.subList(2, args.size()))));
            case "LRANGE" -> {
                List<String> result = listStore.lRange(args.get(1), Integer.parseInt(args.get(2)), Integer.parseInt(args.get(3)));
                out.write(RespEncoder.encodeList(result));
            }
            case "LLEN" -> out.write(RespEncoder.respInteger(listStore.dataSize(args.get(1))));
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
            case "XREAD" -> {
                try {
                    // Syntax A: XREAD STREAMS key1 ... id1 ...
                    // Syntax B: XREAD BLOCK <ms> STREAMS key1 ... id1 ...
                    boolean hasBlock = args.get(1).equalsIgnoreCase("BLOCK");
                    long blockMs = hasBlock ? Long.parseLong(args.get(2)) : 10L;
                    int streamsIdx = hasBlock ? 4 : 2; // index of first key after STREAMS
                    int streamCount = (args.size() - streamsIdx) / 2;
                    List<String> keys = args.subList(streamsIdx, streamsIdx + streamCount);
                    List<String> ids = args.subList(streamsIdx + streamCount, args.size());
                    List<List<StreamEntry>> allEntries = new ArrayList<>();
                    for (int i = 0; i < streamCount; i++) {
                        allEntries.add(streamStore.xread(keys.get(i), ids.get(i), blockMs));
                    }
                    allEntries = allEntries.stream().filter(e -> !e.isEmpty()).toList();
                    if (allEntries.isEmpty()) {
                        out.write(RespEncoder.emptyList());
                    } else {
                        out.write(RespEncoder.encodeXRead(keys, allEntries));
                    }
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