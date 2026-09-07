package io.codecrafters.redis.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RespEncoderTest {

    @Test
    void simpleString() {
        assertEquals("+PONG\r\n", new String(RespEncoder.simpleString("PONG")));
    }

    @Test
    void simpleStringOK() {
        assertEquals("+OK\r\n", new String(RespEncoder.simpleString("OK")));
    }

    @Test
    void bulkString() {
        assertEquals("$3\r\nhey\r\n", new String(RespEncoder.bulkString("hey")));
    }

    @Test
    void bulkStringEmptyString() {
        assertEquals("$0\r\n\r\n", new String(RespEncoder.bulkString("")));
    }

    @Test
    void nullBulkString() {
        assertEquals("$-1\r\n", new String(RespEncoder.nullBulkString()));
    }

    @Test
    void multiBulkStringSingleLineEqualsBulkString() {
        assertEquals("$11\r\nrole:master\r\n", new String(RespEncoder.multiBulkString("role:master")));
    }

    @Test
    void multiBulkStringJoinsLinesWithCrlfInOnePayload() {
        // payload "a\r\nbb\r\nccc" is 10 chars
        assertEquals("$10\r\na\r\nbb\r\nccc\r\n", new String(RespEncoder.multiBulkString("a", "bb", "ccc")));
    }

    @Test
    void multiBulkStringIsOneWellFormedBulkString() {
        String out = new String(RespEncoder.multiBulkString(
                "role:master", "master_replid:abc", "master_repl_offset:0"));

        assertTrue(out.startsWith("$"), out);
        assertEquals(1, out.chars().filter(c -> c == '$').count(), "exactly one length header");

        int firstCrlf = out.indexOf("\r\n");
        int declaredLen = Integer.parseInt(out.substring(1, firstCrlf));
        String payload = out.substring(firstCrlf + 2, out.length() - 2);

        assertEquals(declaredLen, payload.length(), "length prefix must match payload");
        assertTrue(payload.contains("role:master"));
        assertTrue(payload.contains("master_repl_offset:0"));
    }

    @Test
    void error() {
        assertEquals("-ERR unknown command 'FOO'\r\n", new String(RespEncoder.error("unknown command 'FOO'")));
    }
}