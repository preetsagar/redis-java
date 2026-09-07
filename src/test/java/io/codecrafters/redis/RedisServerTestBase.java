package io.codecrafters.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.Socket;

/**
 * Shared lifecycle for the socket-level integration tests: starts a real
 * {@link RedisServer} on {@link #PORT} before each test, waits until it accepts
 * connections, and shuts it down afterwards. Subclasses use {@link #resp} to
 * build RESP command frames.
 */
abstract class RedisServerTestBase {

    protected static final int PORT = 6379;

    private RedisServer server;
    private Thread serverThread;

    @BeforeEach
    void startServer() throws Exception {
        server = new RedisServer(PORT);
        serverThread = new Thread(server::start);
        serverThread.setDaemon(true);
        serverThread.start();
        waitUntilAcceptingConnections();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.stop();
        serverThread.join(1000);
    }

    private void waitUntilAcceptingConnections() throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            try (Socket probe = new Socket("localhost", PORT)) {
                return;
            } catch (IOException notReadyYet) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("server did not start listening on port " + PORT);
    }

    protected static String resp(String... args) {
        StringBuilder sb = new StringBuilder("*").append(args.length).append("\r\n");
        for (String arg : args) {
            sb.append("$").append(arg.length()).append("\r\n").append(arg).append("\r\n");
        }
        return sb.toString();
    }
}