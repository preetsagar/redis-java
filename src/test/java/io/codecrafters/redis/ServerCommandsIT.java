package io.codecrafters.redis;

import org.junit.jupiter.api.Test;

import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class ServerCommandsIT extends RedisServerTestBase {

    @Test
    void infoReplicationReturnsBulkStringWithReplicationFields() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("INFO", "replication").getBytes());
            byte[] buffer = new byte[4096];
            String response = new String(buffer, 0, client.getInputStream().read(buffer));

            assertTrue(response.startsWith("$"), "INFO must reply with a single bulk string, got: " + response);
            assertTrue(response.contains("role:master"), response);
            assertTrue(response.contains("master_replid:"), response);
            assertTrue(response.contains("master_repl_offset:0"), response);
        }
    }

    @Test
    void infoBulkStringLengthPrefixMatchesPayload() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("INFO", "replication").getBytes());
            byte[] buffer = new byte[4096];
            String response = new String(buffer, 0, client.getInputStream().read(buffer));

            int firstCrlf = response.indexOf("\r\n");
            int declaredLen = Integer.parseInt(response.substring(1, firstCrlf));
            String payload = response.substring(firstCrlf + 2, response.length() - 2);

            assertEquals(declaredLen, payload.length(),
                    "length prefix must equal payload length, got: " + response);
        }
    }

    @Test
    void infoReplidIsFortyHexChars() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("INFO", "replication").getBytes());
            byte[] buffer = new byte[4096];
            String response = new String(buffer, 0, client.getInputStream().read(buffer));

            String replid = null;
            for (String line : response.split("\r\n")) {
                if (line.startsWith("master_replid:")) {
                    replid = line.substring("master_replid:".length());
                }
            }
            assertNotNull(replid, "response should carry master_replid: " + response);
            assertTrue(replid.matches("[0-9a-f]{40}"), "replid should be 40 hex chars, got: " + replid);
        }
    }
}