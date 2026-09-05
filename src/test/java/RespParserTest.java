import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class RespParserTest {

    private RespParser parserFor(String input) {
        return new RespParser(new BufferedReader(new StringReader(input)));
    }

    @Test
    void parsesPingCommand() throws IOException {
        String[] args = parserFor("*1\r\n$4\r\nPING\r\n").readCommand();
        assertNotNull(args);
        assertEquals(1, args.length);
        assertEquals("PING", args[0]);
    }

    @Test
    void parsesEchoCommand() throws IOException {
        String[] args = parserFor("*2\r\n$4\r\nECHO\r\n$3\r\nhey\r\n").readCommand();
        assertNotNull(args);
        assertEquals(2, args.length);
        assertEquals("ECHO", args[0]);
        assertEquals("hey", args[1]);
    }

    @Test
    void parsesSetCommand() throws IOException {
        String[] args = parserFor("*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n").readCommand();
        assertNotNull(args);
        assertEquals(3, args.length);
        assertEquals("SET", args[0]);
        assertEquals("foo", args[1]);
        assertEquals("bar", args[2]);
    }

    @Test
    void parsesGetCommand() throws IOException {
        String[] args = parserFor("*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n").readCommand();
        assertNotNull(args);
        assertEquals(2, args.length);
        assertEquals("GET", args[0]);
        assertEquals("foo", args[1]);
    }

    @Test
    void returnsNullOnEmptyInput() throws IOException {
        String[] args = parserFor("").readCommand();
        assertNull(args, "Should return null when there is no input");
    }

    @Test
    void parsesMultipleCommandsSequentially() throws IOException {
        RespParser parser = parserFor("*1\r\n$4\r\nPING\r\n*2\r\n$4\r\nECHO\r\n$5\r\nhello\r\n");

        String[] first = parser.readCommand();
        assertEquals("PING", first[0]);

        String[] second = parser.readCommand();
        assertEquals("ECHO", second[0]);
        assertEquals("hello", second[1]);
    }
}