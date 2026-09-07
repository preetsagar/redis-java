package io.codecrafters.redis;

import io.codecrafters.redis.client.ClientHandler;
import io.codecrafters.redis.command.CommandDispatcher;
import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.store.Database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import static io.codecrafters.redis.Main.getParsed;

public class RedisServer {

    private final int port;
    private final ReplicationInfo replication;
    private ServerSocket serverSocket;

    public RedisServer(int port, String role) {
        this.port = port;
        this.replication = new ReplicationInfo(role);
    }

    public void start() {
        Database db = new Database();
        CommandDispatcher dispatcher = new CommandDispatcher(db, replication);
        if (replication.role().equals("slave")) {
            handshakeWithMaster();
        }
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            // System.out.println("Server listening on port " + port);
            while (true) {
                new Thread(new ClientHandler(serverSocket.accept(), dispatcher)).start();
            }
        } catch (IOException e) {
            if (!serverSocket.isClosed()) {
                System.out.println("Server error: " + e.getMessage());
            }
        }
    }

    private void handshakeWithMaster() {
        String masterHost = getParsed().get("MASTER_HOST");
        int masterPort = Integer.parseInt(getParsed().get("MASTER_PORT"));
        try {
            Socket socket = new Socket(masterHost, masterPort);
            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            send(out, "PING");
            in.readLine();      // +PONG

            send(out, "REPLCONF", "listening-port", String.valueOf(port));
            in.readLine();      // +OK

            send(out, "REPLCONF", "capa", "psync2");
            in.readLine();      // +OK

            send(out, "PSYNC", "?", "-1");
            in.readLine();
        } catch (IOException e) {
            System.out.println("[Error] : Failed while connecting to master " + e.getMessage());
        }
    }

    private static void send(OutputStream out, String... args) throws IOException {
        out.write(RespEncoder.encodeList(List.of(args)));
        out.flush();
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