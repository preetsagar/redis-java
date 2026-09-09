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
        Main.getParsed().keySet().removeAll(java.util.List.of("dir", "appendonly", "appenddirname"));
    }

    @Test
    void appendOnlyDirIsCreatedAtStartup() throws Exception {
        Main.getParsed().put("dir", dir.toString());
        Main.getParsed().put("appendonly", "yes");
        Main.getParsed().put("appenddirname", "myaof");

        startServer();

        assertTrue(Files.isDirectory(dir.resolve("myaof")), "startup should have created <dir>/myaof");
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

    private void startServer() throws Exception {
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
                return;
            } catch (Exception notReadyYet) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("server never came up on port " + port);
    }
}