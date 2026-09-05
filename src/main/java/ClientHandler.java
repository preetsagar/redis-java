import java.io.IOException;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket client;

    public ClientHandler(Socket client) {
        this.client = client;
    }

    @Override
    public void run() {
        try (client) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = client.getInputStream().read(buffer)) != -1) {
                System.out.println("Request: " + new String(buffer, 0, bytesRead));
                client.getOutputStream().write("+PONG\r\n".getBytes());
            }
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }
}