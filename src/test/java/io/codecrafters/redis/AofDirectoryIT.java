package io.codecrafters.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * With {@code --appendonly yes}, the server creates {@code <dir>/<appenddirname>}
 * at startup (before serving clients). Without it, the directory is not created.
 */
class AofDirectoryIT {

    @TempDir
    Path dir;

    private RedisServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        Main.getParsed().keySet().removeAll(
                java.util.List.of("dir", "appendonly", "appenddirname", "appendfilename", "appendfsync"));
    }

    @Test
    void appendOnlyDirAndFileAreCreatedAtStartup() throws Exception {
        Main.getParsed().put("dir", dir.toString());
        Main.getParsed().put("appendonly", "yes");
        Main.getParsed().put("appenddirname", "myaof");
        Main.getParsed().put("appendfilename", "custom.aof");

        startServer();

        Path aofDir = dir.resolve("myaof");
        assertTrue(Files.isDirectory(aofDir), "startup should have created <dir>/myaof");

        Path aofFile = aofDir.resolve("custom.aof.1.incr.aof");
        assertTrue(Files.isRegularFile(aofFile), "startup should have created the .1.incr.aof file");
        assertEquals(0, Files.size(aofFile), "the AOF file should be empty");

        Path manifest = aofDir.resolve("custom.aof.manifest");
        assertTrue(Files.isRegularFile(manifest), "startup should have created the manifest");
        assertEquals("file custom.aof.1.incr.aof seq 1 type i\n", Files.readString(manifest));
    }

    @Test
    void appendOnlyDirIsNotCreatedWhenAppendonlyIsNo() throws Exception {
        Main.getParsed().put("dir", dir.toString());
        Main.getParsed().put("appendonly", "no");
        Main.getParsed().put("appenddirname", "myaof");

        startServer();

        assertFalse(Files.exists(dir.resolve("myaof")));
    }

    @Test
    void startupSucceedsWhenAppendOnlyDirAlreadyExists() throws Exception {
        Files.createDirectory(dir.resolve("myaof"));
        Main.getParsed().put("dir", dir.toString());
        Main.getParsed().put("appendonly", "yes");
        Main.getParsed().put("appenddirname", "myaof");

        startServer();

        assertTrue(Files.isDirectory(dir.resolve("myaof")));
    }

    @Test
    void writeCommandsAreAppendedToTheFileTheManifestNames() throws Exception {
        Path aofDir = Files.createDirectory(dir.resolve("myaof"));
        // manifest points at a non-default filename — the server must follow it
        Files.writeString(aofDir.resolve("custom.aof.manifest"),
                "file weird-name.1.incr.aof seq 1 type i\n");
        Files.write(aofDir.resolve("weird-name.1.incr.aof"), new byte[0]);

        Main.getParsed().put("dir", dir.toString());
        Main.getParsed().put("appendonly", "yes");
        Main.getParsed().put("appenddirname", "myaof");
        Main.getParsed().put("appendfilename", "custom.aof");
        Main.getParsed().put("appendfsync", "always");

        int port = startServer();
        try (Socket client = new Socket("localhost", port)) {
            client.setSoTimeout(2000);
            client.getOutputStream().write("*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\n100\r\n".getBytes());
            assertEquals("+OK\r\n", new String(client.getInputStream().readNBytes(5)));
        }

        assertEquals("*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\n100\r\n",
                Files.readString(aofDir.resolve("weird-name.1.incr.aof")));
        assertFalse(Files.exists(aofDir.resolve("custom.aof.1.incr.aof")),
                "must not touch the default filename when the manifest names another");
    }

    @Test
    void startupReplaysCommandsFromTheManifestsAofFile() throws Exception {
        Path aofDir = Files.createDirectory(dir.resolve("myaof"));
        Files.writeString(aofDir.resolve("custom.aof.manifest"),
                "file replay-me.1.incr.aof seq 1 type i\n");
        Files.writeString(aofDir.resolve("replay-me.1.incr.aof"),
                "*3\r\n$3\r\nSET\r\n$5\r\nmango\r\n$2\r\n42\r\n");

        Main.getParsed().put("dir", dir.toString());
        Main.getParsed().put("appendonly", "yes");
        Main.getParsed().put("appenddirname", "myaof");
        Main.getParsed().put("appendfilename", "custom.aof");

        int port = startServer();
        try (Socket client = new Socket("localhost", port)) {
            client.setSoTimeout(2000);
            client.getOutputStream().write("*2\r\n$3\r\nGET\r\n$5\r\nmango\r\n".getBytes());
            assertEquals("$2\r\n42\r\n", new String(client.getInputStream().readNBytes(8)));
        }
    }

    private int startServer() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        server = new RedisServer(port, "master");
        Thread t = new Thread(server::start);
        t.setDaemon(true);
        t.start();
        for (int i = 0; i < 50; i++) {
            try (Socket probe = new Socket("localhost", port)) {
                return port;
            } catch (Exception notReadyYet) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("server never came up on port " + port);
    }
}