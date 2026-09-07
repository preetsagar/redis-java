package io.codecrafters.redis.command;

import io.codecrafters.redis.client.ClientSession;
import io.codecrafters.redis.store.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @BeforeEach
    void setUp() {
        dispatcher = new CommandDispatcher(new Database());
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
    void infoRoutesToServerCommandAndRepliesWithABulkString() {
        String reply = send("INFO", "replication");
        assertTrue(reply.startsWith("$"), reply);
        assertTrue(reply.contains("master_repl_offset:"), reply);
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