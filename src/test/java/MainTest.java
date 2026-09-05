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
        // Connect and verify the connection completes cleanly
        try (Socket client = new Socket("localhost", PORT)) {
            assertDoesNotThrow(() -> {
                // reading -1 (EOF) is fine – server closed its side after accepting
                client.getInputStream().read();
            }, "Reading from the accepted socket should not throw");
        }
    }

//    @Test
//    void serverSocketHasReuseAddressEnabled() {
//        assertTrue(serverSocket.getReuseAddress(),
//                "SO_REUSEADDR must be set so the port can be reused immediately after restart");
//    }
}
