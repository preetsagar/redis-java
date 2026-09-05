package io.codecrafters.redis;

import io.codecrafters.redis.client.ClientHandler;
import io.codecrafters.redis.store.ListStore;
import io.codecrafters.redis.store.Store;

import java.io.IOException;
import java.net.ServerSocket;

public class RedisServer {

    private final int port;
    private ServerSocket serverSocket;

    public RedisServer(int port) {
        this.port = port;
    }

    public void start() {
        Store store = new Store();
        ListStore listStore = new ListStore();
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            System.out.println("Server listening on port " + port);
            while (true) {
                new Thread(new ClientHandler(serverSocket.accept(), store, listStore)).start();
            }
        } catch (IOException e) {
            if (!serverSocket.isClosed()) {
                System.out.println("Server error: " + e.getMessage());
            }
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.out.println("Error stopping server: " + e.getMessage());
        }
    }
}