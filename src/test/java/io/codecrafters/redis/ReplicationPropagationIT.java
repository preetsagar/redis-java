package io.codecrafters.redis;

import io.codecrafters.redis.protocol.RespParser;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * After a replica finishes the handshake, the master propagates every write
 * command (from any other client) to it as a RESP array, in order, over the
 * replication connection — and sends nothing for non-write commands.
 */
class ReplicationPropagationIT extends RedisServerTestBase {

    @Test
    void masterPropagatesWritesToReplicaInOrder() throws Exception {
        try (Socket replica = new Socket("localhost", PORT);
             Socket writer = new Socket("localhost", PORT)) {
            replica.setSoTimeout(2000);
            RespParser fromMaster = completeHandshake(replica);

            // A normal client issues writes (and one non-write).
            OutputStream writerOut = writer.getOutputStream();
            InputStream writerIn = writer.getInputStream();

            writerOut.write(resp("SET", "foo", "1").getBytes());
            assertEquals("+OK\r\n", new String(writerIn.readNBytes(5)));

            writerOut.write(resp("PING").getBytes()); // not a write — must NOT be propagated
            assertEquals("+PONG\r\n", new String(writerIn.readNBytes(7)));

            writerOut.write(resp("SET", "bar", "2").getBytes());
            assertEquals("+OK\r\n", new String(writerIn.readNBytes(5)));

            // The replication connection receives the writes only, as arrays, in order.
            assertEquals(List.of("SET", "foo", "1"), fromMaster.readCommand());
            assertEquals(List.of("SET", "bar", "2"), fromMaster.readCommand());
        }
    }

    /** Runs PING / REPLCONF x2 / PSYNC, consumes the RDB, returns a parser positioned on the stream. */
    private RespParser completeHandshake(Socket replica) throws Exception {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(replica.getInputStream(), StandardCharsets.ISO_8859_1));
        OutputStream out = replica.getOutputStream();

        out.write(resp("PING").getBytes());
        assertEquals("+PONG", in.readLine());

        out.write(resp("REPLCONF", "listening-port", "6380").getBytes());
        assertEquals("+OK", in.readLine());

        out.write(resp("REPLCONF", "capa", "psync2").getBytes());
        assertEquals("+OK", in.readLine());

        out.write(resp("PSYNC", "?", "-1").getBytes());
        assertTrue(in.readLine().startsWith("+FULLRESYNC "));

        int rdbLen = Integer.parseInt(in.readLine().substring(1)); // "$<len>"
        char[] rdb = new char[rdbLen];
        for (int off = 0; off < rdbLen; ) {
            int n = in.read(rdb, off, rdbLen - off); // RDB payload has no trailing CRLF
            if (n < 0) fail("stream closed mid-RDB");
            off += n;
        }

        return new RespParser(in);
    }
}