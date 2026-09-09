package io.codecrafters.redis.replication;

import io.codecrafters.redis.client.ClientSession;
import io.codecrafters.redis.command.CommandDispatcher;
import io.codecrafters.redis.protocol.RespEncoder;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The replica side of replication. Connects to the master, runs the handshake
 * (PING / REPLCONF x2 / PSYNC), consumes the RDB snapshot, then applies every
 * command the master propagates to the local dataset via {@link CommandDispatcher},
 * discarding the reply — replicas don't answer the master.
 *
 * <p>The one exception is {@code REPLCONF GETACK}, answered with
 * {@code REPLCONF ACK <offset>}, where {@code <offset>} is the number of
 * replication-stream bytes processed <em>before</em> this GETACK (every earlier
 * command counts, including earlier GETACKs; the current one does not).
 */
public class ReplicationClient {

    private final String masterHost;
    private final int masterPort;
    private final int listeningPort;
    private final CommandDispatcher dispatcher;

    public ReplicationClient(String masterHost, int masterPort, int listeningPort,
                             CommandDispatcher dispatcher) {
        this.masterHost = masterHost;
        this.masterPort = masterPort;
        this.listeningPort = listeningPort;
        this.dispatcher = dispatcher;
    }

    /** Runs the whole link (connect, handshake, RDB, command stream) on a daemon thread. */
    public void start() {
        Thread thread = new Thread(this::run, "replication-client");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        try {
            Socket socket = new Socket(masterHost, masterPort);
            OutputStream out = socket.getOutputStream();
            CountingInputStream in = new CountingInputStream(socket.getInputStream());

            handshake(in, out);
            consumeRdb(in);
            in.resetCount(); // the replication offset counts only the command stream after the RDB
            replicate(in, out);
        } catch (Exception e) {
            System.out.println("[replication] link to master ended: " + e.getMessage());
        }
    }

    private void handshake(InputStream in, OutputStream out) throws IOException {
        send(out, "PING");
        readLine(in);                                                   // +PONG
        send(out, "REPLCONF", "listening-port", String.valueOf(listeningPort));
        readLine(in);                                                   // +OK
        send(out, "REPLCONF", "capa", "psync2");
        readLine(in);                                                   // +OK
        send(out, "PSYNC", "?", "-1");
        readLine(in);                                                   // +FULLRESYNC <id> <offset>
    }

    private void consumeRdb(InputStream in) throws IOException {
        String header = readLine(in);                                   // $<len>  (no trailing CRLF after body)
        int length = Integer.parseInt(header.substring(1));
        if (in.readNBytes(length).length != length) {
            throw new IOException("master closed the connection during RDB transfer");
        }
    }

    private void replicate(CountingInputStream in, OutputStream out) throws IOException {
        ClientSession session = dispatcher.newSession();
        while (true) {
            long offsetBeforeCommand = in.count();
            List<String> args = readCommand(in);
            if (args == null) {
                return;
            }
            if (isGetAck(args)) {
                send(out, "REPLCONF", "ACK", Long.toString(offsetBeforeCommand));
            } else {
                dispatcher.dispatch(args, session); // apply locally; reply is discarded
            }
        }
    }

    private static boolean isGetAck(List<String> args) {
        return args.size() >= 2
                && args.get(0).equalsIgnoreCase("REPLCONF")
                && args.get(1).equalsIgnoreCase("GETACK");
    }

    private static void send(OutputStream out, String... args) throws IOException {
        out.write(RespEncoder.encodeList(List.of(args)));
        out.flush();
    }

    // --- minimal byte-level RESP reading (the same stream also carries the binary RDB) ---

    /** Reads one RESP array of bulk strings; null at end of stream. */
    private static List<String> readCommand(InputStream in) throws IOException {
        String header = readLine(in);
        if (header == null) {
            return null;
        }
        if (!header.startsWith("*")) {
            throw new IOException("expected a RESP array from master, got: " + header);
        }
        int count = Integer.parseInt(header.substring(1));
        List<String> args = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int length = Integer.parseInt(readLine(in).substring(1)); // $<len>
            byte[] value = in.readNBytes(length);
            in.read();                                                // \r
            in.read();                                                // \n
            args.add(new String(value, StandardCharsets.UTF_8));
        }
        return args;
    }

    /** Reads bytes up to and consuming a trailing CRLF; null at EOF. */
    private static String readLine(InputStream in) throws IOException {
        int c = in.read();
        if (c == -1) {
            return null;
        }
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (c != -1 && c != '\r') {
            line.write(c);
            c = in.read();
        }
        in.read(); // consume '\n'
        return line.toString(StandardCharsets.UTF_8);
    }

    /** Counts every byte read, so the replica can report its replication offset. */
    private static final class CountingInputStream extends FilterInputStream {

        private long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        long count() {
            return count;
        }

        void resetCount() {
            count = 0;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                count++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                count += n;
            }
            return n;
        }
    }
}