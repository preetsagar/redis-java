package io.codecrafters.redis;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class StringCommandsIT extends RedisServerTestBase {

    @Test
    void setAndGetValue() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "foo", "bar").getBytes());
            assertEquals("+OK\r\n", new String(buffer, 0, in.read(buffer)));

            client.getOutputStream().write(resp("GET", "foo").getBytes());
            assertEquals("$3\r\nbar\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void getMissingKeyReturnsNullBulkString() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("GET", "missing").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("$-1\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void setWithPxExpiresKey() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "foo", "bar", "PX", "100").getBytes());
            in.read(buffer);

            Thread.sleep(150);

            client.getOutputStream().write(resp("GET", "foo").getBytes());
            assertEquals("$-1\r\n", new String(buffer, 0, in.read(buffer)), "Key should have expired");
        }
    }

    @Test
    void setWithExExpiresKey() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "foo", "bar", "EX", "1").getBytes());
            in.read(buffer);

            Thread.sleep(1100);

            client.getOutputStream().write(resp("GET", "foo").getBytes());
            assertEquals("$-1\r\n", new String(buffer, 0, in.read(buffer)), "Key should have expired after 1 second");
        }
    }

    @Test
    void incrOnMissingKeyReturnsOne() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("INCR", "incr-test-1").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals(":1\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void incrOnExistingIntegerIncrements() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "incr-test-2", "5").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("INCR", "incr-test-2").getBytes());
            assertEquals(":6\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void incrMultipleTimesAccumulates() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("INCR", "incr-test-3").getBytes());
            in.read(buffer); // :1
            client.getOutputStream().write(resp("INCR", "incr-test-3").getBytes());
            in.read(buffer); // :2
            client.getOutputStream().write(resp("INCR", "incr-test-3").getBytes());
            assertEquals(":3\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void incrOnNonIntegerReturnsError() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "incr-test-4", "notanumber").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("INCR", "incr-test-4").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertTrue(response.startsWith("-ERR"), "Expected error for non-integer, got: " + response);
        }
    }
}