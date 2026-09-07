package io.codecrafters.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A real {@link RedisServer} master and a real replica in one JVM. The replica
 * runs its handshake against the master (whose {@code ServerCommands} answer
 * {@code REPLCONF} / {@code PSYNC}), and each server keeps its own role because
 * {@link ReplicationInfo} is now per-instance, not static.
 */
class MasterReplicaIT {

    private final List<RedisServer> servers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        servers.forEach(RedisServer::stop);
        Main.getParsed().remove("MASTER_HOST");
        Main.getParsed().remove("MASTER_PORT");
    }

    @Test
    void replicaHandshakesAgainstRealMasterAndBothKeepTheirRole() throws Exception {
        int masterPort = freePort();
        int replicaPort = freePort();

        start(masterPort, "master");
        awaitListening(masterPort);

        Main.getParsed().put("MASTER_HOST", "localhost");
        Main.getParsed().put("MASTER_PORT", String.valueOf(masterPort));
        start(replicaPort, "slave");
        awaitListening(replicaPort);

        assertTrue(infoReplication(masterPort).contains("role:master"),
                "master should report role:master");
        assertTrue(infoReplication(replicaPort).contains("role:slave"),
                "replica should report role:slave (survived the handshake with its own state)");
    }

    private void start(int port, String role) {
        RedisServer server = new RedisServer(port, role);
        servers.add(server);
        Thread t = new Thread(server::start);
        t.setDaemon(true);
        t.start();
    }

    private static String infoReplication(int port) throws Exception {
        try (Socket client = new Socket("localhost", port)) {
            client.setSoTimeout(2000);
            client.getOutputStream().write(resp("INFO", "replication"));
            byte[] buffer = new byte[4096];
            return new String(buffer, 0, client.getInputStream().read(buffer));
        }
    }

    private static byte[] resp(String... args) {
        StringBuilder sb = new StringBuilder("*").append(args.length).append("\r\n");
        for (String arg : args) {
            sb.append("$").append(arg.length()).append("\r\n").append(arg).append("\r\n");
        }
        return sb.toString().getBytes();
    }

    private static void awaitListening(int port) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            try (Socket probe = new Socket("localhost", port)) {
                return;
            } catch (Exception notReadyYet) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("no server accepting connections on port " + port);
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}