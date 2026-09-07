package io.codecrafters.redis.client;

import io.codecrafters.redis.command.CommandDispatcher;
import io.codecrafters.redis.protocol.RespParser;
import io.codecrafters.redis.replication.Replicas;

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
 *
 * <p>If the client issues {@code PSYNC}, this connection becomes a replication
 * link: it is registered for command propagation, and from then on any
 * {@code REPLCONF ACK <offset>} it sends is recorded (and not replied to)
 * instead of being dispatched.
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
        Replicas.Handle replicaLink = null;
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             OutputStream out = client.getOutputStream()) {
            RespParser parser = new RespParser(in);
            List<String> args;
            while ((args = parser.readCommand()) != null) {
                if (replicaLink != null && isReplconfAck(args)) {
                    dispatcher.replicas().recordAck(replicaLink, Long.parseLong(args.get(2)));
                    continue; // a master never replies to REPLCONF ACK
                }

                byte[] response = dispatcher.dispatch(args, session);
                synchronized (out) {
                    out.write(response);
                    out.flush();
                    if (args.get(0).equalsIgnoreCase("PSYNC")) {
                        // register while holding the lock so no propagation slips
                        // in between the RDB payload and this link going live
                        replicaLink = dispatcher.replicas().register(out);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[ERROR]  " + e.getMessage());
        }
    }

    private static boolean isReplconfAck(List<String> args) {
        return args.size() == 3
                && args.get(0).equalsIgnoreCase("REPLCONF")
                && args.get(1).equalsIgnoreCase("ACK");
    }
}