import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.Socket;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    private static final int PORT = 6379;
    private Thread serverThread;

    @BeforeEach
    void startServer() throws Exception {
        serverThread = new Thread(() -> new RedisServer(PORT).start());
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(100); // give server time to bind
    }

    @AfterEach
    void stopServer() throws Exception {
        serverThread.interrupt();
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
            }, "Reading from the accepted socket should not throw");
        }
    }

    @Test
    void serverRepliesWithPongForPingCommand() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("PING").getBytes());
            byte[] buffer = new byte[1024];
            int bytesRead = client.getInputStream().read(buffer);
            String response = new String(buffer, 0, bytesRead);
            assertEquals("+PONG\r\n", response, "Server should respond with +PONG for PING");
        }
    }

    @Test
    void serverHandlesMultipleCommandsOnSameConnection() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            for (int i = 0; i < 3; i++) {
                client.getOutputStream().write(resp("PING").getBytes());
                int bytesRead = in.read(buffer);
                String response = new String(buffer, 0, bytesRead);
                assertEquals("+PONG\r\n", response, "Server should respond with +PONG for each PING");
            }
        }
    }

    @Test
    void serverRepliesWithEchoMessage() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("ECHO", "hey").getBytes());
            byte[] buffer = new byte[1024];
            int bytesRead = client.getInputStream().read(buffer);
            String response = new String(buffer, 0, bytesRead);
            assertEquals("$3\r\nhey\r\n", response, "Server should echo back the message as a bulk string");
        }
    }
}