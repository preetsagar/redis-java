package io.codecrafters.redis;

import io.codecrafters.redis.client.ClientHandler;
import io.codecrafters.redis.command.CommandDispatcher;
import io.codecrafters.redis.rdb.Rdb;
import io.codecrafters.redis.rdb.RdbReader;
import io.codecrafters.redis.replication.ReplicationClient;
import io.codecrafters.redis.replication.Replicas;
import io.codecrafters.redis.store.Database;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;

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
        Rdb reddisDataBase = new Rdb(getParsed().get("dbfilename"), getParsed().get("dir"));
        RdbReader.loadInto(Path.of(reddisDataBase.getDir(), reddisDataBase.getDbFileName()), db.stringStore());
        CommandDispatcher dispatcher = new CommandDispatcher(db, replication, new Replicas(), reddisDataBase);

        if (replication.role().equals("slave")) {
            new ReplicationClient(
                    getParsed().get("MASTER_HOST"),
                    Integer.parseInt(getParsed().get("MASTER_PORT")),
                    port,
                    dispatcher
            ).start();
        }

        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
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