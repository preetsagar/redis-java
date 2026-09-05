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
    void error() {
        assertEquals("-ERR unknown command 'FOO'\r\n", new String(RespEncoder.error("unknown command 'FOO'")));
    }
}