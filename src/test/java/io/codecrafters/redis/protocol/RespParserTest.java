package io.codecrafters.redis.protocol;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RespParserTest {

    private RespParser parserFor(String input) {
        return new RespParser(new BufferedReader(new StringReader(input)));
    }

    @Test
    void parsesPingCommand() throws IOException {
        List<String> args = parserFor("*1\r\n$4\r\nPING\r\n").readCommand();
        assertNotNull(args);
        assertEquals(1, args.size());
        assertEquals("PING", args.get(0));
    }

    @Test
    void parsesEchoCommand() throws IOException {
        List<String> args = parserFor("*2\r\n$4\r\nECHO\r\n$3\r\nhey\r\n").readCommand();
        assertNotNull(args);
        assertEquals(2, args.size());
        assertEquals("ECHO", args.get(0));
        assertEquals("hey", args.get(1));
    }

    @Test
    void parsesSetCommand() throws IOException {
        List<String> args = parserFor("*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n").readCommand();
        assertNotNull(args);
        assertEquals(3, args.size());
        assertEquals("SET", args.get(0));
        assertEquals("foo", args.get(1));
        assertEquals("bar", args.get(2));
    }

    @Test
    void parsesGetCommand() throws IOException {
        List<String> args = parserFor("*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n").readCommand();
        assertNotNull(args);
        assertEquals(2, args.size());
        assertEquals("GET", args.get(0));
        assertEquals("foo", args.get(1));
    }

    @Test
    void returnsNullOnEmptyInput() throws IOException {
        assertNull(parserFor("").readCommand());
    }

    @Test
    void parsesMultipleCommandsSequentially() throws IOException {
        RespParser parser = parserFor("*1\r\n$4\r\nPING\r\n*2\r\n$4\r\nECHO\r\n$5\r\nhello\r\n");

        List<String> first = parser.readCommand();
        assertEquals("PING", first.get(0));

        List<String> second = parser.readCommand();
        assertEquals("ECHO", second.get(0));
        assertEquals("hello", second.get(1));
    }
}