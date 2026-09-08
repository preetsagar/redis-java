package io.codecrafters.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * With {@code --dir} / {@code --dbfilename} pointing at an RDB file, the server
 * loads its keys at startup and {@code KEYS *} returns them.
 */
class KeyspaceFromRdbIT {

    @TempDir
    Path dir;

    private RedisServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        Main.getParsed().remove("dir");
        Main.getParsed().remove("dbfilename");
    }

    @Test
    void keysStarReturnsKeysLoadedFromTheRdbFile() throws Exception {
        ByteArrayOutputStream rdb = new ByteArrayOutputStream();
        rdb.writeBytes("REDIS0011".getBytes(StandardCharsets.US_ASCII));
        rdb.write(0xFE);
        rdb.write(0x00);                       // select db 0
        rdb.write(0xFB);
        rdb.write(0x01);
        rdb.write(0x00);                       // resize: 1 key, 0 with expiry
        rdb.write(0x00);                       // value type: string
        writeString(rdb, "mango");
        writeString(rdb, "42");
        rdb.write(0xFF);
        rdb.writeBytes(new byte[8]);           // checksum (unvalidated)
        Files.write(dir.resolve("dump.rdb"), rdb.toByteArray());

        int port = freePort();
        Main.getParsed().put("dir", dir.toString());
        Main.getParsed().put("dbfilename", "dump.rdb");

        server = new RedisServer(port, "master");
        Thread thread = new Thread(server::start);
        thread.setDaemon(true);
        thread.start();
        awaitListening(port);

        try (Socket client = new Socket("localhost", port)) {
            client.setSoTimeout(2000);
            client.getOutputStream().write("*2\r\n$4\r\nKEYS\r\n$1\r\n*\r\n".getBytes());
            byte[] buffer = new byte[256];
            String reply = new String(buffer, 0, client.getInputStream().read(buffer));
            assertEquals("*1\r\n$5\r\nmango\r\n", reply);
        }
    }

    private static void writeString(ByteArrayOutputStream out, String s) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        out.write(raw.length); // assumes length < 64
        out.writeBytes(raw);
    }

    private static void awaitListening(int port) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            try (Socket probe = new Socket("localhost", port)) {
                return;
            } catch (Exception notReadyYet) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("no server accepting connections on port " + port);
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}