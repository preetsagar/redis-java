package io.codecrafters.redis;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionCommandsIT extends RedisServerTestBase {

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
}