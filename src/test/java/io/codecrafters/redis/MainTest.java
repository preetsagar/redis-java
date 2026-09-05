package io.codecrafters.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    private static final int PORT = 6379;
    private RedisServer server;
    private Thread serverThread;

    @BeforeEach
    void startServer() throws Exception {
        server = new RedisServer(PORT);
        serverThread = new Thread(server::start);
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(100);
    }

    @AfterEach
    void stopServer() throws Exception {
        server.stop();
        serverThread.join(1000);
    }

    private static String resp(String... args) {
        StringBuilder sb = new StringBuilder("*").append(args.length).append("\r\n");
        for (String arg : args) {
            sb.append("$").append(arg.length()).append("\r\n").append(arg).append("\r\n");
        }
        return sb.toString();
    }

    @Test
    void serverBindsToPort6379() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            assertEquals(PORT, client.getPort(), "Server should be listening on port " + PORT);
        }
    }

    @Test
    void clientCanConnectToServer() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            assertTrue(client.isConnected(), "Client should be able to connect to the server");
        }
    }

    @Test
    void serverAcceptsConnectionWithoutError() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("PING").getBytes());
            assertDoesNotThrow(() -> {
                byte[] buffer = new byte[1024];
                int bytesRead = client.getInputStream().read(buffer);
                assertTrue(bytesRead > 0, "Server should send a response");
            });
        }
    }

    @Test
    void serverRepliesWithPongForPingCommand() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("PING").getBytes());
            byte[] buffer = new byte[1024];
            int bytesRead = client.getInputStream().read(buffer);
            assertEquals("+PONG\r\n", new String(buffer, 0, bytesRead));
        }
    }

    @Test
    void serverHandlesMultipleCommandsOnSameConnection() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];
            for (int i = 0; i < 3; i++) {
                client.getOutputStream().write(resp("PING").getBytes());
                assertEquals("+PONG\r\n", new String(buffer, 0, in.read(buffer)));
            }
        }
    }

    @Test
    void serverRepliesWithEchoMessage() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("ECHO", "hey").getBytes());
            byte[] buffer = new byte[1024];
            int bytesRead = client.getInputStream().read(buffer);
            assertEquals("$3\r\nhey\r\n", new String(buffer, 0, bytesRead));
        }
    }

    @Test
    void setAndGetValue() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "foo", "bar").getBytes());
            assertEquals("+OK\r\n", new String(buffer, 0, in.read(buffer)));

            client.getOutputStream().write(resp("GET", "foo").getBytes());
            assertEquals("$3\r\nbar\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void getMissingKeyReturnsNullBulkString() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("GET", "missing").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("$-1\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void setWithPxExpiresKey() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "foo", "bar", "PX", "100").getBytes());
            in.read(buffer);

            Thread.sleep(150);

            client.getOutputStream().write(resp("GET", "foo").getBytes());
            assertEquals("$-1\r\n", new String(buffer, 0, in.read(buffer)), "Key should have expired");
        }
    }

    @Test
    void setWithExExpiresKey() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "foo", "bar", "EX", "1").getBytes());
            in.read(buffer);

            Thread.sleep(1100);

            client.getOutputStream().write(resp("GET", "foo").getBytes());
            assertEquals("$-1\r\n", new String(buffer, 0, in.read(buffer)), "Key should have expired after 1 second");
        }
    }

    @Test
    void rpushReturnsOneForFirstElement() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("RPUSH", "rpush-test-1", "orange").getBytes());
            byte[] buffer = new byte[1024];
            int bytesRead = client.getInputStream().read(buffer);
            assertEquals(":1\r\n", new String(buffer, 0, bytesRead));
        }
    }

    @Test
    void rpushReturnsSizeAfterMultipleInserts() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "rpush-test-2", "orange").getBytes());
            in.read(buffer); // consume :1

            client.getOutputStream().write(resp("RPUSH", "rpush-test-2", "mango").getBytes());
            assertEquals(":2\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void rpushDifferentKeysAreIndependent() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "rpush-test-3a", "a").getBytes());
            in.read(buffer); // consume :1

            client.getOutputStream().write(resp("RPUSH", "rpush-test-3b", "x").getBytes());
            assertEquals(":1\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }
}
