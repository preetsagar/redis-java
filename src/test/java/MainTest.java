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

    @Test
    void setAndGetValue() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "foo", "bar").getBytes());
            String setResponse = new String(buffer, 0, in.read(buffer));
            assertEquals("+OK\r\n", setResponse);

            client.getOutputStream().write(resp("GET", "foo").getBytes());
            String getResponse = new String(buffer, 0, in.read(buffer));
            assertEquals("$3\r\nbar\r\n", getResponse);
        }
    }

    @Test
    void getMissingKeyReturnsNullBulkString() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("GET", "missing").getBytes());
            byte[] buffer = new byte[1024];
            String response = new String(buffer, 0, client.getInputStream().read(buffer));
            assertEquals("$-1\r\n", response);
        }
    }

    @Test
    void setWithPxExpiresKey() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "foo", "bar", "PX", "100").getBytes());
            in.read(buffer); // consume +OK

            Thread.sleep(150);

            client.getOutputStream().write(resp("GET", "foo").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertEquals("$-1\r\n", response, "Key should have expired");
        }
    }

    @Test
    void setWithExExpiresKey() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "foo", "bar", "EX", "1").getBytes());
            in.read(buffer); // consume +OK

            Thread.sleep(1100);

            client.getOutputStream().write(resp("GET", "foo").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertEquals("$-1\r\n", response, "Key should have expired after 1 second");
        }
    }
}