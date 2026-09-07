package io.codecrafters.redis;

import io.codecrafters.redis.client.ClientHandler;
import io.codecrafters.redis.command.CommandDispatcher;
import io.codecrafters.redis.store.Database;

import java.io.IOException;
import java.net.ServerSocket;

public class RedisServer {

    private final int port;
    private ServerSocket serverSocket;

    public static String getRole() {
        return role;
    }

    private static String role;

    public RedisServer(int port, String role) {
        this.port = port;
        this.role = role;
    }

    public void start() {
        Database db = new Database();
        CommandDispatcher dispatcher = new CommandDispatcher(db);
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            System.out.println("Server listening on port " + port);
            while (true) {
                new Thread(new ClientHandler(serverSocket.accept(), dispatcher)).start();
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