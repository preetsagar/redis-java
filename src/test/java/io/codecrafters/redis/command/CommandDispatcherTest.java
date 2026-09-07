package io.codecrafters.redis.command;

import io.codecrafters.redis.ReplicationInfo;
import io.codecrafters.redis.client.ClientSession;
import io.codecrafters.redis.replication.Replicas;
import io.codecrafters.redis.store.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Socket-free tests for {@link CommandDispatcher}: they call
 * {@code dispatch(args, session)} directly and assert on the RESP bytes, so the
 * transaction / WATCH logic is covered without spinning up a server. A second
 * {@link ClientSession} stands in for "another connection".
 */
class CommandDispatcherTest {

    private CommandDispatcher dispatcher;
    private ClientSession session;
    private Replicas replicas;

    @BeforeEach
    void setUp() {
        replicas = new Replicas();
        dispatcher = new CommandDispatcher(new Database(), new ReplicationInfo("master"), replicas);
        session = dispatcher.newSession();
    }

    private String send(String... args) {
        return new String(dispatcher.dispatch(List.of(args), session));
    }

    private String sendFrom(ClientSession other, String... args) {
        return new String(dispatcher.dispatch(List.of(args), other));
    }

    // --- routing ---

    @Test
    void pingRoutesToConnectionCommand() {
        assertEquals("+PONG\r\n", send("PING"));
    }

    @Test
    void commandNameIsCaseInsensitive() {
        assertEquals("+PONG\r\n", send("ping"));
    }

    @Test
    void unknownCommandReturnsError() {
        assertTrue(send("NOSUCH", "x").startsWith("-ERR unknown command 'NOSUCH'"));
    }

    @Test
    void dataCommandRunsAgainstTheStore() {
        assertEquals("+OK\r\n", send("SET", "k", "v"));
        assertEquals("$1\r\nv\r\n", send("GET", "k"));
    }

    @Test
    void writeCommandsPropagateToReplicasVerbatimAndOthersDoNot() {
        java.io.ByteArrayOutputStream link = new java.io.ByteArrayOutputStream();
        replicas.register(link);

        send("SET", "foo", "bar");
        send("PING");              // not a write — nothing propagated
        send("GET", "foo");        // not a write
        send("SET", "baz", "1");

        assertEquals(
                "*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"
              + "*3\r\n$3\r\nSET\r\n$3\r\nbaz\r\n$1\r\n1\r\n",
                link.toString(StandardCharsets.ISO_8859_1));
    }

    @Test
    void waitWithNoWritesYetReturnsReplicaCountWithoutBlocking() {
        replicas.register(new java.io.ByteArrayOutputStream());
        replicas.register(new java.io.ByteArrayOutputStream());

        // no write has been dispatched, so replication offset is 0 and every
        // replica is trivially caught up — returns immediately even though
        // 5 > 2 replicas are requested with a 10s timeout.
        assertEquals(":2\r\n", send("WAIT", "5", "10000"));
    }

    @Test
    void infoRoutesToServerCommandAndRepliesWithABulkString() {
        String reply = send("INFO", "replication");
        assertTrue(reply.startsWith("$"), reply);
        assertTrue(reply.contains("master_repl_offset:"), reply);
    }

    // --- master side of the replication handshake ---

    @Test
    void replconfListeningPortIsAcknowledged() {
        assertEquals("+OK\r\n", send("REPLCONF", "listening-port", "6380"));
    }

    @Test
    void replconfCapaIsAcknowledged() {
        assertEquals("+OK\r\n", send("REPLCONF", "capa", "psync2"));
    }

    @Test
    void psyncRepliesWithFullResyncLineThenEmptyRdbFrame() {
        byte[] reply = dispatcher.dispatch(List.of("PSYNC", "?", "-1"), session);
        String text = new String(reply, StandardCharsets.ISO_8859_1);

        // line 1: +FULLRESYNC <40 hex> 0\r\n   (exactly one leading '+')
        int firstCrlf = text.indexOf("\r\n");
        String fullresync = text.substring(0, firstCrlf);
        assertTrue(fullresync.matches("\\+FULLRESYNC [0-9a-f]{40} 0"), fullresync);

        // then: $<len>\r\n<len bytes>, NO trailing CRLF
        int headerStart = firstCrlf + 2;
        assertEquals('$', text.charAt(headerStart));
        int secondCrlf = text.indexOf("\r\n", headerStart);
        int declaredLen = Integer.parseInt(text.substring(headerStart + 1, secondCrlf));

        int rdbStart = secondCrlf + 2;
        assertEquals(declaredLen, reply.length - rdbStart, "RDB byte count must match $<len>");
        assertFalse(text.endsWith("\r\n"), "RDB frame must not end with CRLF");
        assertEquals("REDIS", new String(reply, rdbStart, 5, StandardCharsets.ISO_8859_1),
                "RDB payload should start with the REDIS magic");
    }

    // --- MULTI / EXEC / DISCARD ---

    @Test
    void multiReturnsOk() {
        assertEquals("+OK\r\n", send("MULTI"));
    }

    @Test
    void commandsAreQueuedInsideMulti() {
        send("MULTI");
        assertEquals("+QUEUED\r\n", send("SET", "k", "1"));
        assertEquals("+QUEUED\r\n", send("INCR", "k"));
    }

    @Test
    void execRunsQueuedCommandsInOrder() {
        send("MULTI");
        send("SET", "k", "41");
        send("INCR", "k");
        assertEquals("*2\r\n+OK\r\n:42\r\n", send("EXEC"));
    }

    @Test
    void execOnEmptyQueueReturnsEmptyArray() {
        send("MULTI");
        assertEquals("*0\r\n", send("EXEC"));
    }

    @Test
    void execWithoutMultiIsError() {
        assertTrue(send("EXEC").startsWith("-ERR"));
    }

    @Test
    void discardWithoutMultiIsError() {
        assertTrue(send("DISCARD").startsWith("-ERR"));
    }

    @Test
    void discardDropsQueuedCommands() {
        send("MULTI");
        send("SET", "k", "9");
        assertEquals("+OK\r\n", send("DISCARD"));
        assertEquals("$-1\r\n", send("GET", "k")); // SET never ran
    }

    @Test
    void commandsRunNormallyAfterExec() {
        send("MULTI");
        send("SET", "k", "1");
        send("EXEC");
        assertEquals("$1\r\n1\r\n", send("GET", "k"));
    }

    // --- WATCH ---

    @Test
    void unwatchReturnsOk() {
        assertEquals("+OK\r\n", send("UNWATCH"));
    }

    @Test
    void watchInsideMultiIsError() {
        send("MULTI");
        assertTrue(send("WATCH", "k").startsWith("-ERR"));
    }

    @Test
    void execSucceedsWhenWatchedKeyUntouched() {
        send("SET", "k", "1");
        send("WATCH", "k");
        send("MULTI");
        send("INCR", "k");
        assertEquals("*1\r\n:2\r\n", send("EXEC"));
    }

    @Test
    void execAbortsWhenWatchedKeyModifiedByAnotherSession() {
        send("SET", "k", "1");
        send("WATCH", "k");
        send("MULTI");
        send("INCR", "k");

        sendFrom(dispatcher.newSession(), "SET", "k", "99");

        assertEquals("*-1\r\n", send("EXEC"));
        assertEquals("$2\r\n99\r\n", send("GET", "k")); // queued INCR did not run
    }

    @Test
    void unwatchClearsWatchSoExecSucceeds() {
        send("SET", "k", "1");
        send("WATCH", "k");
        send("UNWATCH");
        sendFrom(dispatcher.newSession(), "SET", "k", "99");
        send("MULTI");
        send("INCR", "k");
        assertEquals("*1\r\n:100\r\n", send("EXEC"));
    }

    @Test
    void abortedExecClearsWatchState() {
        send("SET", "k", "1");
        send("WATCH", "k");
        sendFrom(dispatcher.newSession(), "SET", "k", "2");

        send("MULTI");
        send("INCR", "k");
        assertEquals("*-1\r\n", send("EXEC")); // aborted

        // fresh transaction, no active WATCH → runs
        send("MULTI");
        send("INCR", "k");
        assertEquals("*1\r\n:3\r\n", send("EXEC"));
    }

    @Test
    void discardClearsWatchState() {
        send("SET", "k", "1");
        send("WATCH", "k");
        send("MULTI");
        send("DISCARD");
        sendFrom(dispatcher.newSession(), "SET", "k", "99");

        send("MULTI");
        send("INCR", "k");
        assertEquals("*1\r\n:100\r\n", send("EXEC"));
    }
}