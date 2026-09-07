package io.codecrafters.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A replica ({@code --replicaof}) must open a connection to its master on
 * startup and send {@code PING}. Here a plain {@link ServerSocket} stands in for
 * the master and asserts on the bytes it receives.
 */
class ReplicationHandshakeIT {

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
    void replicaSendsPingToMasterOnStartup() throws Exception {
        try (ServerSocket master = new ServerSocket(0)) {
            master.setSoTimeout(2000);

            Main.getParsed().put("MASTER_HOST", "localhost");
            Main.getParsed().put("MASTER_PORT", String.valueOf(master.getLocalPort()));

            replica = new RedisServer(0, "slave");
            Thread replicaThread = new Thread(replica::start);
            replicaThread.setDaemon(true);
            replicaThread.start();

            try (Socket fromReplica = master.accept()) {
                InputStream in = fromReplica.getInputStream();
                byte[] buffer = new byte[64];
                int read = in.read(buffer);
                assertEquals("*1\r\n$4\r\nPING\r\n", new String(buffer, 0, read));
            }
        }
    }
}