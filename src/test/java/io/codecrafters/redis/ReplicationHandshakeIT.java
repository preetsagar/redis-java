package io.codecrafters.redis;

import io.codecrafters.redis.protocol.RespParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A replica ({@code --replicaof}) must run the replication handshake against its
 * master on startup: {@code PING}, {@code REPLCONF listening-port <port>},
 * {@code REPLCONF capa psync2}, then {@code PSYNC ? -1} — waiting for a reply
 * after each. Here a plain {@link ServerSocket} plays the master and drives the
 * dialogue.
 */
class ReplicationHandshakeIT {

    private static final String MASTER_REPLID = "8371b4fb1155b71f4a04d3e1bc3e18c4a990aeeb";

    private RedisServer replica;

    @AfterEach
    void tearDown() {
        if (replica != null) {
            replica.stop();
        }
        Main.getParsed().remove("MASTER_HOST");
        Main.getParsed().remove("MASTER_PORT");
    }

    @Test
    void replicaRunsFullHandshake() throws Exception {
        int replicaPort = freePort();

        try (ServerSocket master = new ServerSocket(0)) {
            master.setSoTimeout(2000);

            Main.getParsed().put("MASTER_HOST", "localhost");
            Main.getParsed().put("MASTER_PORT", String.valueOf(master.getLocalPort()));

            replica = new RedisServer(replicaPort, "slave");
            Thread replicaThread = new Thread(replica::start);
            replicaThread.setDaemon(true);
            replicaThread.start();

            try (Socket conn = master.accept()) {
                conn.setSoTimeout(2000); // fail fast instead of hanging if the replica stalls
                RespParser fromReplica = new RespParser(
                        new BufferedReader(new InputStreamReader(conn.getInputStream())));
                OutputStream toReplica = conn.getOutputStream();

                assertEquals(List.of("PING"), fromReplica.readCommand());
                reply(toReplica, "+PONG\r\n");

                assertEquals(List.of("REPLCONF", "listening-port", String.valueOf(replicaPort)),
                        fromReplica.readCommand());
                reply(toReplica, "+OK\r\n");

                assertEquals(List.of("REPLCONF", "capa", "psync2"), fromReplica.readCommand());
                reply(toReplica, "+OK\r\n");

                assertEquals(List.of("PSYNC", "?", "-1"), fromReplica.readCommand());
                reply(toReplica, "+FULLRESYNC " + MASTER_REPLID + " 0\r\n");
            }
        }
    }

    private static void reply(OutputStream out, String resp) throws Exception {
        out.write(resp.getBytes());
        out.flush();
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}