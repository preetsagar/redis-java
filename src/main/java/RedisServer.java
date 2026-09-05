import java.io.IOException;
import java.net.ServerSocket;

public class RedisServer {

    private final int port;

    public RedisServer(int port) {
        this.port = port;
    }

    public void start() {
        Store store = new Store();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            System.out.println("Server listening on port " + port);
            while (true) {
                new Thread(new ClientHandler(serverSocket.accept(), store)).start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
}