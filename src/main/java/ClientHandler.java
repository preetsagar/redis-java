import java.io.*;
import java.net.Socket;
import java.util.HashMap;

public class ClientHandler implements Runnable {

    private final Socket client;
    private HashMap<String, String> hashMap;

    public ClientHandler(Socket client) {
        this.client = client;
        hashMap = new HashMap<>();
    }

    @Override
    public void run() {
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             OutputStream out = client.getOutputStream()) {
            RespParser parser = new RespParser(in);
            String[] args;
            while ((args = parser.readCommand()) != null) {
                handleCommand(args, out);
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
            case "SET" -> {
                hashMap.put(args[1], args[2]);
                out.write(getSimpleString("OK"));
            }
            case "GET" -> {
                out.write(encodeResponse(hashMap.get(args[1])));
            }
            default -> out.write(("-ERR unknown command '" + args[0] + "'\r\n").getBytes());
        }
    }

    private static byte[] encodeResponse(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append("$").append(str.length()).append("\r\n").append(str).append("\r\n");
        return sb.toString().getBytes();
    }

    private  static byte[] getSimpleString(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append("+").append(str).append("\r\n");
        return sb.toString().getBytes();
    }
}
