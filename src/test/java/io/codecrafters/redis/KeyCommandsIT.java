package io.codecrafters.redis;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class KeyCommandsIT extends RedisServerTestBase {

    @Test
    void typeReturnsStringForStringKey() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "type-test-1", "hello").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("TYPE", "type-test-1").getBytes());
            assertEquals("+string\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void typeReturnsListForListKey() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "type-test-2", "a").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("TYPE", "type-test-2").getBytes());
            assertEquals("+list\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void typeReturnsNoneForMissingKey() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("TYPE", "type-missing").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("+none\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void typeReturnsStreamForStreamKey() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("XADD", "xadd-type-test", "1-0", "foo", "bar").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("TYPE", "xadd-type-test").getBytes());
            assertEquals("+stream\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void typeReturnsNoneForExpiredKey() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("SET", "type-test-3", "val", "PX", "100").getBytes());
            in.read(buffer);

            Thread.sleep(150);

            client.getOutputStream().write(resp("TYPE", "type-test-3").getBytes());
            assertEquals("+none\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }
}