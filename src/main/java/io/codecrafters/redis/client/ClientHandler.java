package io.codecrafters.redis.client;

import io.codecrafters.redis.command.CommandDispatcher;
import io.codecrafters.redis.protocol.RespParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

/**
 * Owns one client socket: reads RESP commands, hands each to the shared
 * {@link CommandDispatcher} along with this connection's {@link ClientSession},
 * and writes the reply back.
 */
public class ClientHandler implements Runnable {

    private final Socket client;
    private final CommandDispatcher dispatcher;
    private final ClientSession session;

    public ClientHandler(Socket client, CommandDispatcher dispatcher) {
        this.client = client;
        this.dispatcher = dispatcher;
        this.session = dispatcher.newSession();
    }

    @Override
    public void run() {
//        String clientInfo = client.getInetAddress().getHostAddress() + ":" + client.getPort();
//        System.out.println("[CONNECTED  " + clientInfo + "]");
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             OutputStream out = client.getOutputStream()) {
            RespParser parser = new RespParser(in);
            List<String> args;
            while ((args = parser.readCommand()) != null) {
//                System.out.println("[REQUEST  " + clientInfo + "] → " + args);
                byte[] response = dispatcher.dispatch(args, session);
//                System.out.println("[RESPONSE " + clientInfo + "] ← " + new String(response).replace("\r\n", "\\r\\n"));
                out.write(response);
            }
        } catch (IOException e) {
//            System.out.println("[ERROR]    " + clientInfo + " — " + e.getMessage());
            System.out.println("[ERROR]  "  + e.getMessage());
        }
//        System.out.println("[DISCONNECTED] " + clientInfo);
    }
}