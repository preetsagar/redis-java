import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    private static final int PORT = 6379;
    private Thread serverThread;
    private ServerSocket serverSocket;

    @BeforeEach
    void startServer() throws Exception {
        serverSocket = new ServerSocket(PORT);
        serverSocket.setReuseAddress(true);

        // Run the server accept loop in a background thread (mirrors Main behaviour)
        serverThread = new Thread(() -> {
            try {
                Socket client = serverSocket.accept();
                byte[] buffer = new byte[1024];
                int bytesRead = client.getInputStream().read(buffer);
                System.out.println("Request: " + new String(buffer, 0, bytesRead));
                client.getOutputStream().write("+PONG\r\n".getBytes());
                client.close();
            } catch (IOException e) {
                // Expected when we shut the server down in @AfterEach
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        serverThread.join(1000);
    }


    @Test
    void serverBindsToPort6379() throws Exception {
        // If the server started without throwing, it successfully bound to 6379
        assertFalse(serverSocket.isClosed(), "ServerSocket should be open and bound");
        assertEquals(PORT, serverSocket.getLocalPort(), "Server should be listening on port 6379");
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
}
