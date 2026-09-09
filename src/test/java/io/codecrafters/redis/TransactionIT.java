package io.codecrafters.redis;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class TransactionIT extends RedisServerTestBase {

    @Test
    void multiReturnsOk() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("MULTI").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("+OK\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void commandsInsideMultiReturnQueued() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("MULTI").getBytes());
            in.read(buffer); // +OK

            client.getOutputStream().write(resp("SET", "multi-key-1", "41").getBytes());
            assertEquals("+QUEUED\r\n", new String(buffer, 0, in.read(buffer)));

            client.getOutputStream().write(resp("INCR", "multi-key-1").getBytes());
            assertEquals("+QUEUED\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void execExecutesQueuedCommands() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("MULTI").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("SET", "multi-key-2", "41").getBytes());
            in.read(buffer); // QUEUED

            client.getOutputStream().write(resp("INCR", "multi-key-2").getBytes());
            in.read(buffer); // QUEUED

            client.getOutputStream().write(resp("EXEC").getBytes());
            String response = new String(buffer, 0, in.read(buffer));

            // *2 results: +OK and :42
            assertEquals("*2\r\n+OK\r\n:42\r\n", response);
        }
    }

    @Test
    void execOnEmptyQueueReturnsEmptyArray() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("MULTI").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("EXEC").getBytes());
            assertEquals("*0\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void execWithoutMultiReturnsError() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("EXEC").getBytes());
            byte[] buffer = new byte[1024];
            String response = new String(buffer, 0, client.getInputStream().read(buffer));
            assertTrue(response.startsWith("-ERR"), "Expected error, got: " + response);
        }
    }

    @Test
    void discardClearsQueue() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("MULTI").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("SET", "multi-key-3", "99").getBytes());
            in.read(buffer); // QUEUED

            client.getOutputStream().write(resp("DISCARD").getBytes());
            assertEquals("+OK\r\n", new String(buffer, 0, in.read(buffer)));

            // Key should not have been set
            client.getOutputStream().write(resp("GET", "multi-key-3").getBytes());
            assertEquals("$-1\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void execResultsReflectActualExecution() throws Exception {
        // SET foo 41, INCR foo → results should be +OK and :42
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("MULTI").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("SET", "multi-exec-1", "41").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("INCR", "multi-exec-1").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("GET", "multi-exec-1").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("EXEC").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertEquals("*3\r\n+OK\r\n:42\r\n$2\r\n42\r\n", response);
        }
    }

    @Test
    void commandsAfterExecAreNotQueued() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("MULTI").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("SET", "multi-after-1", "1").getBytes());
            in.read(buffer); // QUEUED
            client.getOutputStream().write(resp("EXEC").getBytes());
            in.read(buffer); // *1\r\n+OK\r\n

            // After EXEC, normal commands should execute immediately
            client.getOutputStream().write(resp("GET", "multi-after-1").getBytes());
            assertEquals("$1\r\n1\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void discardWithoutMultiReturnsError() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("DISCARD").getBytes());
            byte[] buffer = new byte[1024];
            String response = new String(buffer, 0, client.getInputStream().read(buffer));
            assertTrue(response.startsWith("-ERR"), "Expected error, got: " + response);
        }
    }

    @Test
    void execWithGetReturnsValue() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            // Pre-set a key before MULTI
            client.getOutputStream().write(resp("SET", "multi-get-1", "hello").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("MULTI").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("GET", "multi-get-1").getBytes());
            in.read(buffer); // QUEUED

            client.getOutputStream().write(resp("EXEC").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertEquals("*1\r\n$5\r\nhello\r\n", response);
        }
    }

    @Test
    void multipleIncrInsideMulti() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("MULTI").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("INCR", "multi-incr-1").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("INCR", "multi-incr-1").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("INCR", "multi-incr-1").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("EXEC").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertEquals("*3\r\n:1\r\n:2\r\n:3\r\n", response);
        }
    }

    @Test
    void execContinuesAfterCommandError() throws Exception {
        // SET foo xyz → OK
        // INCR foo    → ERR (xyz is not an integer)
        // SET bar 7   → OK
        // Other commands still execute despite the error in the middle
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("MULTI").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("SET", "multi-err-foo", "xyz").getBytes());
            assertEquals("+QUEUED\r\n", new String(buffer, 0, in.read(buffer)));

            client.getOutputStream().write(resp("INCR", "multi-err-foo").getBytes());
            assertEquals("+QUEUED\r\n", new String(buffer, 0, in.read(buffer)));

            client.getOutputStream().write(resp("SET", "multi-err-bar", "7").getBytes());
            assertEquals("+QUEUED\r\n", new String(buffer, 0, in.read(buffer)));

            client.getOutputStream().write(resp("EXEC").getBytes());
            String response = new String(buffer, 0, in.read(buffer));

            // 3 results: OK, ERR, OK
            assertTrue(response.startsWith("*3\r\n"), "Expected 3 results, got: " + response);
            assertTrue(response.contains("+OK\r\n"), "Should contain OK for SET");
            assertTrue(response.contains("-ERR"), "Should contain error for INCR on non-integer");

            // bar should have been set despite the error on foo
            client.getOutputStream().write(resp("GET", "multi-err-bar").getBytes());
            assertEquals("$1\r\n7\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }
}