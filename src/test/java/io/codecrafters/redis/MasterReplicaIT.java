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

    @Test
    void replicaAppliesWritesPropagatedByMaster() throws Exception {
        int masterPort = freePort();
        int replicaPort = freePort();

        start(masterPort, "master");
        awaitListening(masterPort);

        Main.getParsed().put("MASTER_HOST", "localhost");
        Main.getParsed().put("MASTER_PORT", String.valueOf(masterPort));
        start(replicaPort, "slave");
        awaitListening(replicaPort);

        awaitConnectedSlaves(masterPort, 1); // replica must be registered before we write

        try (Socket writer = new Socket("localhost", masterPort)) {
            writer.setSoTimeout(2000);
            writer.getOutputStream().write(resp("SET", "foo", "123"));
            assertEquals("+OK\r\n", new String(writer.getInputStream().readNBytes(5)));
            writer.getOutputStream().write(resp("SET", "counter", "9"));
            assertEquals("+OK\r\n", new String(writer.getInputStream().readNBytes(5)));
        }

        assertEquals("$3\r\n123\r\n", awaitGet(replicaPort, "foo"));
        assertEquals("$1\r\n9\r\n", awaitGet(replicaPort, "counter"));
    }

    @Test
    void waitReturnsHowManyReplicasAckedTheLatestWrite() throws Exception {
        int masterPort = freePort();
        start(masterPort, "master");
        awaitListening(masterPort);

        Main.getParsed().put("MASTER_HOST", "localhost");
        Main.getParsed().put("MASTER_PORT", String.valueOf(masterPort));
        start(freePort(), "slave");
        start(freePort(), "slave");
        awaitConnectedSlaves(masterPort, 2);

        try (Socket client = new Socket("localhost", masterPort)) {
            client.setSoTimeout(3000);

            client.getOutputStream().write(resp("SET", "foo", "123"));
            assertEquals("+OK\r\n", new String(client.getInputStream().readNBytes(5)));

            // both replicas process the write and ACK well within the timeout
            client.getOutputStream().write(resp("WAIT", "2", "1000"));
            assertEquals(":2\r\n", readReply(client));

            // asking for more replicas than exist: returns 2 once the timeout expires
            long start = System.currentTimeMillis();
            client.getOutputStream().write(resp("WAIT", "5", "300"));
            assertEquals(":2\r\n", readReply(client));
            assertTrue(System.currentTimeMillis() - start >= 250, "WAIT should have blocked ~timeout");
        }
    }

    private static String readReply(Socket client) throws Exception {
        java.io.InputStream in = client.getInputStream();
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            sb.append((char) c);
            if (c == '\n') {
                break;
            }
        }
        return sb.toString();
    }

    private static void awaitConnectedSlaves(int masterPort, int n) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (infoReplication(masterPort).contains("connected_slaves:" + n)) {
                return;
            }
            Thread.sleep(20);
        }
        fail("master never registered " + n + " replica(s)");
    }

    // Polls GET on the replica until it returns a non-null value (replication is async).
    private static String awaitGet(int replicaPort, String key) throws Exception {
        String reply = "$-1\r\n";
        for (int attempt = 0; attempt < 100; attempt++) {
            try (Socket client = new Socket("localhost", replicaPort)) {
                client.setSoTimeout(2000);
                client.getOutputStream().write(resp("GET", key));
                byte[] buffer = new byte[256];
                reply = new String(buffer, 0, client.getInputStream().read(buffer));
            }
            if (!reply.equals("$-1\r\n")) {
                return reply;
            }
            Thread.sleep(20);
        }
        return reply;
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