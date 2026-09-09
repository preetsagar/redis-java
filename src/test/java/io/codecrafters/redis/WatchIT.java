package io.codecrafters.redis;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class WatchIT extends RedisServerTestBase {

    @Test
    void watchReturnsOk() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("WATCH", "w-key-1").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("+OK\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void watchAcceptsMultipleKeys() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("WATCH", "w-key-2a", "w-key-2b", "w-key-2c").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("+OK\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void unwatchReturnsOk() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("UNWATCH").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("+OK\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void watchInsideMultiReturnsError() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("MULTI").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("WATCH", "w-key-3").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertTrue(response.startsWith("-ERR"), "Expected error, got: " + response);
        }
    }

    @Test
    void execSucceedsWhenWatchedKeyNotModified() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("SET", "w-ok-1", "1").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("WATCH", "w-ok-1").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("MULTI").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("INCR", "w-ok-1").getBytes());
            in.read(buffer); // QUEUED

            client.getOutputStream().write(resp("EXEC").getBytes());
            assertEquals("*1\r\n:2\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void execAbortsWhenWatchedKeyModifiedByAnotherClient() throws Exception {
        try (Socket c1 = new Socket("localhost", PORT);
             Socket c2 = new Socket("localhost", PORT)) {
            InputStream in1 = c1.getInputStream();
            InputStream in2 = c2.getInputStream();
            byte[] buf = new byte[4096];

            c1.getOutputStream().write(resp("SET", "w-abort-1", "1").getBytes());
            in1.read(buf);

            c1.getOutputStream().write(resp("WATCH", "w-abort-1").getBytes());
            in1.read(buf);

            c1.getOutputStream().write(resp("MULTI").getBytes());
            in1.read(buf);
            c1.getOutputStream().write(resp("INCR", "w-abort-1").getBytes());
            in1.read(buf); // QUEUED

            // Another client modifies the watched key
            c2.getOutputStream().write(resp("SET", "w-abort-1", "99").getBytes());
            in2.read(buf);

            c1.getOutputStream().write(resp("EXEC").getBytes());
            assertEquals("*-1\r\n", new String(buf, 0, in1.read(buf)),
                    "EXEC should abort (nil) when a watched key changed");

            // The queued INCR must NOT have run
            c1.getOutputStream().write(resp("GET", "w-abort-1").getBytes());
            assertEquals("$2\r\n99\r\n", new String(buf, 0, in1.read(buf)));
        }
    }

    @Test
    void execSucceedsWhenWatchedKeyModifiedBeforeWatch() throws Exception {
        try (Socket c1 = new Socket("localhost", PORT);
             Socket c2 = new Socket("localhost", PORT)) {
            InputStream in1 = c1.getInputStream();
            InputStream in2 = c2.getInputStream();
            byte[] buf = new byte[4096];

            // Modification happens BEFORE the WATCH — must not count
            c2.getOutputStream().write(resp("SET", "w-before-1", "5").getBytes());
            in2.read(buf);

            c1.getOutputStream().write(resp("WATCH", "w-before-1").getBytes());
            in1.read(buf);

            c1.getOutputStream().write(resp("MULTI").getBytes());
            in1.read(buf);
            c1.getOutputStream().write(resp("INCR", "w-before-1").getBytes());
            in1.read(buf);

            c1.getOutputStream().write(resp("EXEC").getBytes());
            assertEquals("*1\r\n:6\r\n", new String(buf, 0, in1.read(buf)));
        }
    }

    @Test
    void unwatchClearsWatchSoExecSucceeds() throws Exception {
        try (Socket c1 = new Socket("localhost", PORT);
             Socket c2 = new Socket("localhost", PORT)) {
            InputStream in1 = c1.getInputStream();
            InputStream in2 = c2.getInputStream();
            byte[] buf = new byte[4096];

            c1.getOutputStream().write(resp("SET", "w-unwatch-1", "1").getBytes());
            in1.read(buf);

            c1.getOutputStream().write(resp("WATCH", "w-unwatch-1").getBytes());
            in1.read(buf);

            c1.getOutputStream().write(resp("UNWATCH").getBytes());
            in1.read(buf);

            // Modify after UNWATCH — should no longer matter
            c2.getOutputStream().write(resp("SET", "w-unwatch-1", "99").getBytes());
            in2.read(buf);

            c1.getOutputStream().write(resp("MULTI").getBytes());
            in1.read(buf);
            c1.getOutputStream().write(resp("INCR", "w-unwatch-1").getBytes());
            in1.read(buf);

            c1.getOutputStream().write(resp("EXEC").getBytes());
            assertEquals("*1\r\n:100\r\n", new String(buf, 0, in1.read(buf)));
        }
    }

    @Test
    void abortedExecClearsWatchState() throws Exception {
        try (Socket c1 = new Socket("localhost", PORT);
             Socket c2 = new Socket("localhost", PORT)) {
            InputStream in1 = c1.getInputStream();
            InputStream in2 = c2.getInputStream();
            byte[] buf = new byte[4096];

            c1.getOutputStream().write(resp("SET", "w-reset-1", "1").getBytes());
            in1.read(buf);

            c1.getOutputStream().write(resp("WATCH", "w-reset-1").getBytes());
            in1.read(buf);

            c2.getOutputStream().write(resp("SET", "w-reset-1", "2").getBytes());
            in2.read(buf);

            c1.getOutputStream().write(resp("MULTI").getBytes());
            in1.read(buf);
            c1.getOutputStream().write(resp("INCR", "w-reset-1").getBytes());
            in1.read(buf);
            c1.getOutputStream().write(resp("EXEC").getBytes());
            assertEquals("*-1\r\n", new String(buf, 0, in1.read(buf))); // aborted

            // A fresh transaction with no WATCH must succeed even though the key
            // was modified during the previous (now-cleared) watch session
            c1.getOutputStream().write(resp("MULTI").getBytes());
            in1.read(buf);
            c1.getOutputStream().write(resp("INCR", "w-reset-1").getBytes());
            in1.read(buf);
            c1.getOutputStream().write(resp("EXEC").getBytes());
            assertEquals("*1\r\n:3\r\n", new String(buf, 0, in1.read(buf)));
        }
    }

    @Test
    void discardClearsWatchState() throws Exception {
        try (Socket c1 = new Socket("localhost", PORT);
             Socket c2 = new Socket("localhost", PORT)) {
            InputStream in1 = c1.getInputStream();
            InputStream in2 = c2.getInputStream();
            byte[] buf = new byte[4096];

            c1.getOutputStream().write(resp("SET", "w-discard-1", "1").getBytes());
            in1.read(buf);

            c1.getOutputStream().write(resp("WATCH", "w-discard-1").getBytes());
            in1.read(buf);

            c1.getOutputStream().write(resp("MULTI").getBytes());
            in1.read(buf);
            c1.getOutputStream().write(resp("DISCARD").getBytes());
            in1.read(buf);

            // Watched key changes, but the watch was dropped by DISCARD
            c2.getOutputStream().write(resp("SET", "w-discard-1", "99").getBytes());
            in2.read(buf);

            c1.getOutputStream().write(resp("MULTI").getBytes());
            in1.read(buf);
            c1.getOutputStream().write(resp("INCR", "w-discard-1").getBytes());
            in1.read(buf);
            c1.getOutputStream().write(resp("EXEC").getBytes());
            assertEquals("*1\r\n:100\r\n", new String(buf, 0, in1.read(buf)));
        }
    }
}