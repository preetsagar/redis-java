package io.codecrafters.redis;

import io.codecrafters.redis.client.ClientHandler;
import io.codecrafters.redis.command.CommandDispatcher;
import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.store.Database;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import static io.codecrafters.redis.Main.getParsed;

public class RedisServer {

    private final int port;
    private ServerSocket serverSocket;
    private static String role;
    private static String master_replid;

    public static Long getMaster_repl_offset() {
        return master_repl_offset;
    }

    public static String getMaster_replid() {
        return master_replid;
    }

    private static Long master_repl_offset;

    public static String getRole() {
        return role;
    }


    public RedisServer(int port, String role) {
        this.port = port;
        RedisServer.role = role;
        master_replid = "8371b4fb1155b71f4a04d3e1bc3e18c4a990aeeb";
        master_repl_offset = 0l;
    }

    public void start() {
        Database db = new Database();
        CommandDispatcher dispatcher = new CommandDispatcher(db);
        if(role.equals("slave")) {
            try {
                Socket socket = new Socket(getParsed().get("MASTER_HOST"), Integer.parseInt(Main.getParsed().get("MASTER_PORT")));
                OutputStream out = socket.getOutputStream();
                out.write(RespEncoder.encodeList(List.of("PING")));
            } catch (IOException e) {
                // throw new RuntimeException(e);
                System.out.println("[Error] : Failed while connecting to master "  + e.getMessage());
            }
        }
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