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
    void startServer() {
        serverThread = new Thread(() -> new RedisServer(PORT).start());
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        serverThread.interrupt();
        serverThread.join(1000);
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
            client.getOutputStream().write("ping\r\n".getBytes());
            assertDoesNotThrow(() -> {
                byte[] buffer = new byte[1024];
                int bytesRead = client.getInputStream().read(buffer);
                assertTrue(bytesRead > 0, "Server should send a response");
            }, "Reading from the accepted socket should not throw");
        }
    }

    @Test
    void serverRepliesWithPongForAnyInput() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write("hello\r\n".getBytes());
            byte[] buffer = new byte[1024];
            int bytesRead = client.getInputStream().read(buffer);
            String response = new String(buffer, 0, bytesRead);
            assertEquals("+PONG\r\n", response, "Server should respond with +PONG for any input");
        }
    }

    @Test
    void serverHandlesMultipleCommandsOnSameConnection() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            String[] commands = {"ping\r\n", "hello\r\n", "world\r\n"};
            for (String command : commands) {
                client.getOutputStream().write(command.getBytes());
                int bytesRead = in.read(buffer);
                String response = new String(buffer, 0, bytesRead);
                assertEquals("+PONG\r\n", response,
                    "Server should respond with +PONG for command: " + command.trim());
            }
        }
    }
}
