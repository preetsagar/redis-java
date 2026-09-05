import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket client;

    public ClientHandler(Socket client) {
        this.client = client;
    }

    @Override
    public void run() {
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             OutputStream out = client.getOutputStream()) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("*")) {
                    int count = Integer.parseInt(line.substring(1));
                    String[] args = new String[count];
                    for (int i = 0; i < count; i++) {
                        in.readLine(); // skip $<len> line
                        args[i] = in.readLine();
                    }
                    handleCommand(args, out);
                }
            }
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    private void handleCommand(String[] args, OutputStream out) throws IOException {
        switch (args[0].toUpperCase()) {
            case "PING" -> out.write("+PONG\r\n".getBytes());
            case "ECHO" -> {
                String msg = args[1];
                out.write(("$" + msg.length() + "\r\n" + msg + "\r\n").getBytes());
            }
            default -> out.write(("-ERR unknown command '" + args[0] + "'\r\n").getBytes());
        }
    }
}
