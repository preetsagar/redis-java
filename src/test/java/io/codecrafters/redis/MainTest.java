package io.codecrafters.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    private static final int PORT = 6379;
    private RedisServer server;
    private Thread serverThread;

    @BeforeEach
    void startServer() throws Exception {
        server = new RedisServer(PORT);
        serverThread = new Thread(server::start);
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(100);
    }

    @AfterEach
    void stopServer() throws Exception {
        server.stop();
        serverThread.join(1000);
    }

    private static String resp(String... args) {
        StringBuilder sb = new StringBuilder("*").append(args.length).append("\r\n");
        for (String arg : args) {
            sb.append("$").append(arg.length()).append("\r\n").append(arg).append("\r\n");
        }
        return sb.toString();
    }

    @Test
    void serverBindsToPort6379() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            assertEquals(PORT, client.getPort(), "Server should be listening on port " + PORT);
        }
    }

    @Test
    void clientCanConnectToServer() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            assertTrue(client.isConnected(), "Client should be able to connect to the server");
        }
    }

    @Test
    void serverAcceptsConnectionWithoutError() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("PING").getBytes());
            assertDoesNotThrow(() -> {
                byte[] buffer = new byte[1024];
                int bytesRead = client.getInputStream().read(buffer);
                assertTrue(bytesRead > 0, "Server should send a response");
            });
        }
    }

    @Test
    void serverRepliesWithPongForPingCommand() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("PING").getBytes());
            byte[] buffer = new byte[1024];
            int bytesRead = client.getInputStream().read(buffer);
            assertEquals("+PONG\r\n", new String(buffer, 0, bytesRead));
        }
    }

    @Test
    void serverHandlesMultipleCommandsOnSameConnection() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];
            for (int i = 0; i < 3; i++) {
                client.getOutputStream().write(resp("PING").getBytes());
                assertEquals("+PONG\r\n", new String(buffer, 0, in.read(buffer)));
            }
        }
    }

    @Test
    void serverRepliesWithEchoMessage() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("ECHO", "hey").getBytes());
            byte[] buffer = new byte[1024];
            int bytesRead = client.getInputStream().read(buffer);
            assertEquals("$3\r\nhey\r\n", new String(buffer, 0, bytesRead));
        }
    }

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
    void rpushReturnsOneForFirstElement() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("RPUSH", "rpush-test-1", "orange").getBytes());
            byte[] buffer = new byte[1024];
            int bytesRead = client.getInputStream().read(buffer);
            assertEquals(":1\r\n", new String(buffer, 0, bytesRead));
        }
    }

    @Test
    void rpushReturnsSizeAfterMultipleInserts() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "rpush-test-2", "orange").getBytes());
            in.read(buffer); // consume :1

            client.getOutputStream().write(resp("RPUSH", "rpush-test-2", "mango").getBytes());
            assertEquals(":2\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void rpushDifferentKeysAreIndependent() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "rpush-test-3a", "a").getBytes());
            in.read(buffer); // consume :1

            client.getOutputStream().write(resp("RPUSH", "rpush-test-3b", "x").getBytes());
            assertEquals(":1\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lrangeReturnsAllElements() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lrange-test-1", "a", "b", "c").getBytes());
            in.read(buffer); // consume :3

            client.getOutputStream().write(resp("LRANGE", "lrange-test-1", "0", "2").getBytes());
            assertEquals("*3\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lrangeWithNegativeEndReturnsAllElements() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lrange-test-2", "x", "y").getBytes());
            in.read(buffer); // consume :2

            client.getOutputStream().write(resp("LRANGE", "lrange-test-2", "0", "-1").getBytes());
            assertEquals("*2\r\n$1\r\nx\r\n$1\r\ny\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lrangeOnMissingKeyReturnsEmptyArray() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("LRANGE", "lrange-test-missing", "0", "-1").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("*0\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void lrangeLastTwoElementsWithNegativeIndices() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lrange-test-4", "a", "b", "c", "d", "e").getBytes());
            in.read(buffer); // consume :5

            client.getOutputStream().write(resp("LRANGE", "lrange-test-4", "-2", "-1").getBytes());
            assertEquals("*2\r\n$1\r\nd\r\n$1\r\ne\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lrangeAllExceptLastTwoWithNegativeEnd() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lrange-test-5", "a", "b", "c", "d", "e").getBytes());
            in.read(buffer); // consume :5

            client.getOutputStream().write(resp("LRANGE", "lrange-test-5", "0", "-3").getBytes());
            assertEquals("*3\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lrangeNegativeStartOutOfRangeTreatedAsZero() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lrange-test-6", "a", "b", "c", "d", "e").getBytes());
            in.read(buffer); // consume :5

            client.getOutputStream().write(resp("LRANGE", "lrange-test-6", "-6", "-1").getBytes());
            assertEquals("*5\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n$1\r\nd\r\n$1\r\ne\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lrangeSubsetRange() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lrange-test-3", "a", "b", "c", "d", "e").getBytes());
            in.read(buffer); // consume :5

            client.getOutputStream().write(resp("LRANGE", "lrange-test-3", "1", "3").getBytes());
            assertEquals("*3\r\n$1\r\nb\r\n$1\r\nc\r\n$1\r\nd\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpushSingleElementReturnsOne() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("LPUSH", "lpush-test-1", "blueberry").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals(":1\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void lpushMultipleValuesPrependsInCorrectOrder() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("LPUSH", "lpush-test-2", "blueberry").getBytes());
            in.read(buffer); // consume :1

            client.getOutputStream().write(resp("LPUSH", "lpush-test-2", "grape", "pear").getBytes());
            in.read(buffer); // consume :3

            client.getOutputStream().write(resp("LRANGE", "lpush-test-2", "0", "-1").getBytes());
            assertEquals("*3\r\n$4\r\npear\r\n$5\r\ngrape\r\n$9\r\nblueberry\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpushReturnsSizeAfterEachInsert() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("LPUSH", "lpush-test-3", "a").getBytes());
            assertEquals(":1\r\n", new String(buffer, 0, in.read(buffer)));

            client.getOutputStream().write(resp("LPUSH", "lpush-test-3", "b", "c").getBytes());
            assertEquals(":3\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpushSingleElementLrangeValidation() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("LPUSH", "lpush-lrange-1", "blueberry").getBytes());
            in.read(buffer); // consume :1

            client.getOutputStream().write(resp("LRANGE", "lpush-lrange-1", "0", "-1").getBytes());
            assertEquals("*1\r\n$9\r\nblueberry\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpushSequentialInsertsLrangeValidation() throws Exception {
        // Each LPUSH prepends, so final order is reverse of insertion order
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("LPUSH", "lpush-lrange-2", "a").getBytes());
            in.read(buffer); // consume :1
            client.getOutputStream().write(resp("LPUSH", "lpush-lrange-2", "b").getBytes());
            in.read(buffer); // consume :2
            client.getOutputStream().write(resp("LPUSH", "lpush-lrange-2", "c").getBytes());
            in.read(buffer); // consume :3

            client.getOutputStream().write(resp("LRANGE", "lpush-lrange-2", "0", "-1").getBytes());
            assertEquals("*3\r\n$1\r\nc\r\n$1\r\nb\r\n$1\r\na\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpushSubsetLrangeValidation() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("LPUSH", "lpush-lrange-3", "blueberry").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("LPUSH", "lpush-lrange-3", "grape", "pear").getBytes());
            in.read(buffer); // list is now [pear, grape, blueberry]

            // fetch only first 2
            client.getOutputStream().write(resp("LRANGE", "lpush-lrange-3", "0", "1").getBytes());
            assertEquals("*2\r\n$4\r\npear\r\n$5\r\ngrape\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpushThenRpushLrangeValidation() throws Exception {
        // Mixing LPUSH and RPUSH — verify combined order
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lpush-lrange-4", "b").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("LPUSH", "lpush-lrange-4", "a").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("RPUSH", "lpush-lrange-4", "c").getBytes());
            in.read(buffer); // list is now [a, b, c]

            client.getOutputStream().write(resp("LRANGE", "lpush-lrange-4", "0", "-1").getBytes());
            assertEquals("*3\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void llenMissingKeyReturnsZero() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("LLEN", "llen-missing").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals(":0\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void llenAfterRpush() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "llen-test-1", "a", "b", "c").getBytes());
            in.read(buffer); // consume :3

            client.getOutputStream().write(resp("LLEN", "llen-test-1").getBytes());
            assertEquals(":3\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void llenAfterLpush() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("LPUSH", "llen-test-2", "a", "b").getBytes());
            in.read(buffer); // consume :2

            client.getOutputStream().write(resp("LLEN", "llen-test-2").getBytes());
            assertEquals(":2\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void llenAfterMixedPushes() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "llen-test-3", "a", "b").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("LPUSH", "llen-test-3", "c").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("LLEN", "llen-test-3").getBytes());
            assertEquals(":3\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpopReturnsFirstElement() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lpop-test-1", "a", "b", "c").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("LPOP", "lpop-test-1").getBytes());
            assertEquals("$1\r\na\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpopRemovesElementFromList() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lpop-test-2", "a", "b", "c").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("LPOP", "lpop-test-2").getBytes());
            in.read(buffer); // consume "a"

            client.getOutputStream().write(resp("LRANGE", "lpop-test-2", "0", "-1").getBytes());
            assertEquals("*2\r\n$1\r\nb\r\n$1\r\nc\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpopOnMissingKeyReturnsNullBulkString() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("LPOP", "lpop-missing").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("$-1\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void lpopOnEmptyListReturnsNullBulkString() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lpop-test-3", "a").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("LPOP", "lpop-test-3").getBytes());
            in.read(buffer); // remove "a"

            client.getOutputStream().write(resp("LPOP", "lpop-test-3").getBytes());
            assertEquals("$-1\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpopWithCountReturnsArray() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lpop-test-4", "a", "b", "c", "d").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("LPOP", "lpop-test-4", "2").getBytes());
            assertEquals("*2\r\n$1\r\na\r\n$1\r\nb\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpopWithCountRemovesElements() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lpop-test-5", "a", "b", "c").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("LPOP", "lpop-test-5", "2").getBytes());
            in.read(buffer); // consume [a, b]

            client.getOutputStream().write(resp("LRANGE", "lpop-test-5", "0", "-1").getBytes());
            assertEquals("*1\r\n$1\r\nc\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void lpopWithCountGreaterThanSizeReturnsAll() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "lpop-test-6", "a", "b").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("LPOP", "lpop-test-6", "10").getBytes());
            assertEquals("*2\r\n$1\r\na\r\n$1\r\nb\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void blpopReturnsImmediatelyIfElementExists() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("RPUSH", "blpop-test-1", "foobar").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("BLPOP", "blpop-test-1", "1").getBytes());
            assertEquals("*2\r\n$12\r\nblpop-test-1\r\n$6\r\nfoobar\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void blpopTimesOutOnEmptyList() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("BLPOP", "blpop-test-2", "0.1").getBytes());
            client.setSoTimeout(500);
            byte[] buffer = new byte[1024];
            int bytesRead = client.getInputStream().read(buffer);
            assertEquals("*-1\r\n", new String(buffer, 0, bytesRead));
        }
    }

    @Test
    void blpopBlocksUntilElementIsPushed() throws Exception {
        try (Socket blocker = new Socket("localhost", PORT);
             Socket pusher = new Socket("localhost", PORT)) {

            // blocker sends BLPOP with 2 second timeout
            blocker.getOutputStream().write(resp("BLPOP", "blpop-test-3", "2").getBytes());

            // pusher waits 200ms then pushes
            Thread.sleep(200);
            pusher.getOutputStream().write(resp("RPUSH", "blpop-test-3", "hello").getBytes());
            pusher.getInputStream().read(new byte[1024]); // consume :1

            // blocker should now receive the element
            byte[] buffer = new byte[1024];
            int bytesRead = blocker.getInputStream().read(buffer);
            assertEquals("*2\r\n$12\r\nblpop-test-3\r\n$5\r\nhello\r\n", new String(buffer, 0, bytesRead));
        }
    }

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
    void xaddReturnsEntryId() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("XADD", "xadd-test-1", "0-1", "foo", "bar").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("$3\r\n0-1\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void xaddReturnsFullId() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("XADD", "xadd-test-2", "1526919030474-0", "temperature", "36", "humidity", "95").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("$15\r\n1526919030474-0\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
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
    void xaddRejectsSameId() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("XADD", "xadd-val-1", "1-1", "foo", "bar").getBytes());
            in.read(buffer); // consume "1-1"

            client.getOutputStream().write(resp("XADD", "xadd-val-1", "1-1", "bar", "baz").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertTrue(response.startsWith("-ERR"), "Expected error for duplicate ID, got: " + response);
        }
    }

    @Test
    void xaddRejectsSmallerMillis() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("XADD", "xadd-val-2", "2-0", "foo", "bar").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XADD", "xadd-val-2", "1-0", "bar", "baz").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertTrue(response.startsWith("-ERR"), "Expected error for smaller millis, got: " + response);
        }
    }

    @Test
    void xaddRejectsSmallerSequenceWithSameMillis() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("XADD", "xadd-val-3", "1-5", "foo", "bar").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XADD", "xadd-val-3", "1-3", "bar", "baz").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertTrue(response.startsWith("-ERR"), "Expected error for smaller sequence, got: " + response);
        }
    }

    @Test
    void xaddRejectsZeroZeroId() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("XADD", "xadd-val-4", "0-0", "foo", "bar").getBytes());
            byte[] buffer = new byte[1024];
            String response = new String(buffer, 0, client.getInputStream().read(buffer));
            assertTrue(response.startsWith("-ERR"), "Expected error for 0-0 ID, got: " + response);
        }
    }

    @Test
    void xaddAcceptsGreaterSequenceWithSameMillis() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("XADD", "xadd-val-5", "1-1", "foo", "bar").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XADD", "xadd-val-5", "1-2", "bar", "baz").getBytes());
            assertEquals("$3\r\n1-2\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void xaddDifferentKeysHaveIndependentIds() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("XADD", "xadd-ind-1", "5-0", "foo", "bar").getBytes());
            in.read(buffer);

            // Different key can start from 1-0 regardless of xadd-ind-1's last ID
            client.getOutputStream().write(resp("XADD", "xadd-ind-2", "1-0", "foo", "bar").getBytes());
            assertEquals("$3\r\n1-0\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void xaddWildcardSequenceOnEmptyStreamReturnsZero() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("XADD", "xadd-wc-1", "5-*", "foo", "bar").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("$3\r\n5-0\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void xaddWildcardZeroMillisOnEmptyStreamReturnsZeroOne() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("XADD", "xadd-wc-2", "0-*", "foo", "bar").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("$3\r\n0-1\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void xaddWildcardSequenceIncrementsWhenMillisMatches() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("XADD", "xadd-wc-3", "3-2", "foo", "bar").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XADD", "xadd-wc-3", "3-*", "baz", "qux").getBytes());
            assertEquals("$3\r\n3-3\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void xaddWildcardSequenceResetsWhenMillisIncreases() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("XADD", "xadd-wc-4", "0-1", "foo", "bar").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XADD", "xadd-wc-4", "1-*", "baz", "qux").getBytes());
            assertEquals("$3\r\n1-0\r\n", new String(buffer, 0, in.read(buffer)));
        }
    }

    @Test
    void xaddWildcardIdIsUsedForSubsequentValidation() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            // generates 2-0
            client.getOutputStream().write(resp("XADD", "xadd-wc-5", "2-*", "foo", "bar").getBytes());
            in.read(buffer);

            // 2-0 is now the last ID, so explicit 2-0 must be rejected
            client.getOutputStream().write(resp("XADD", "xadd-wc-5", "2-0", "baz", "qux").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertTrue(response.startsWith("-ERR"), "Expected error for duplicate ID, got: " + response);
        }
    }

    @Test
    void xaddFullWildcardReturnsValidId() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            long before = System.currentTimeMillis();
            client.getOutputStream().write(resp("XADD", "xadd-fw-1", "*", "foo", "bar").getBytes());

            byte[] buffer = new byte[1024];
            String response = new String(buffer, 0, client.getInputStream().read(buffer));
            long after = System.currentTimeMillis();

            // Response is a bulk string: $<len>\r\n<id>\r\n
            assertTrue(response.startsWith("$"), "Expected bulk string, got: " + response);
            String id = response.split("\r\n")[1];
            String[] parts = id.split("-");
            assertEquals(2, parts.length);
            long millis = Long.parseLong(parts[0]);
            long seq = Long.parseLong(parts[1]);
            assertTrue(millis >= before && millis <= after, "Millis should be within test window");
            assertEquals(0, seq);
        }
    }

    @Test
    void xrangeReturnsAllEntries() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("XADD", "xrange-1", "1-0", "temperature", "36", "humidity", "95").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xrange-1", "2-0", "temperature", "37", "humidity", "94").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XRANGE", "xrange-1", "1-0", "2-0").getBytes());
            String response = new String(buffer, 0, in.read(buffer));

            // *2 entries, each entry is *2[id, *4[fields]]
            assertTrue(response.startsWith("*2\r\n"), "Expected 2 entries, got: " + response);
            assertTrue(response.contains("1-0"), "Should contain first entry ID");
            assertTrue(response.contains("2-0"), "Should contain second entry ID");
            assertTrue(response.contains("temperature"), "Should contain field name");
            assertTrue(response.contains("36"), "Should contain field value");
        }
    }

    @Test
    void xrangeSingleEntry() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("XADD", "xrange-2", "1-0", "foo", "bar").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XRANGE", "xrange-2", "1-0", "1-0").getBytes());
            String response = new String(buffer, 0, in.read(buffer));

            assertEquals("*1\r\n*2\r\n$3\r\n1-0\r\n*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n", response);
        }
    }

    @Test
    void xrangeStartWithoutSequenceNumber() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("XADD", "xrange-3", "1-0", "a", "1").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xrange-3", "1-1", "b", "2").getBytes());
            in.read(buffer);

            // Start "1" without seq → defaults to 1-0
            client.getOutputStream().write(resp("XRANGE", "xrange-3", "1", "1-1").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertTrue(response.startsWith("*2\r\n"), "Expected 2 entries, got: " + response);
        }
    }

    @Test
    void xrangeEndWithoutSequenceNumber() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("XADD", "xrange-4", "1-0", "a", "1").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xrange-4", "1-9", "b", "2").getBytes());
            in.read(buffer);

            // End "1" without seq → defaults to 1-MAX → includes all seq under millis 1
            client.getOutputStream().write(resp("XRANGE", "xrange-4", "1-0", "1").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertTrue(response.startsWith("*2\r\n"), "Expected 2 entries, got: " + response);
        }
    }

    @Test
    void xrangeExcludesOutOfBoundsEntries() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("XADD", "xrange-5", "1-0", "a", "1").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xrange-5", "2-0", "b", "2").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xrange-5", "3-0", "c", "3").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XRANGE", "xrange-5", "2-0", "2-0").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertTrue(response.startsWith("*1\r\n"), "Expected 1 entry, got: " + response);
            assertTrue(response.contains("2-0"));
        }
    }

    @Test
    void xreadReturnsEntriesAfterGivenId() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("XADD", "xread-1", "1-0", "temperature", "36", "humidity", "95").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xread-1", "2-0", "temperature", "37", "humidity", "94").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XREAD", "STREAMS", "xread-1", "1-0").getBytes());
            String response = new String(buffer, 0, in.read(buffer));

            // *1 stream, *2 [key, entries], key=xread-1, *1 entry
            assertTrue(response.startsWith("*1\r\n*2\r\n"), "Expected XREAD wrapper, got: " + response);
            assertTrue(response.contains("xread-1"), "Should contain stream key");
            assertTrue(response.contains("2-0"), "Should contain second entry ID");
            assertFalse(response.contains("1-0\r\n"), "Should NOT contain first entry (exclusive)");
        }
    }

    @Test
    void xreadIsExclusiveOfGivenId() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("XADD", "xread-2", "1-0", "foo", "bar").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XREAD", "STREAMS", "xread-2", "1-0").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            // No entries after 1-0, so the entries array is *0
            assertTrue(response.contains("*-1\r\n"), "Expected empty entries array, got: " + response);
        }
    }

    @Test
    void xreadExactRespEncoding() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("XADD", "xread-3", "1-0", "foo", "bar").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xread-3", "2-0", "baz", "qux").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XREAD", "STREAMS", "xread-3", "1-0").getBytes());
            String response = new String(buffer, 0, in.read(buffer));

            String expected =
                    "*1\r\n" +
                    "*2\r\n" +
                    "$7\r\nxread-3\r\n" +
                    "*1\r\n" +
                    "*2\r\n" +
                    "$3\r\n2-0\r\n" +
                    "*2\r\n" +
                    "$3\r\nbaz\r\n" +
                    "$3\r\nqux\r\n";
            assertEquals(expected, response);
        }
    }

    @Test
    void xreadFromZeroReturnsAllEntries() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("XADD", "xread-4", "1-0", "a", "1").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xread-4", "2-0", "b", "2").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XREAD", "STREAMS", "xread-4", "0-0").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertTrue(response.contains("1-0"), "Should contain first entry");
            assertTrue(response.contains("2-0"), "Should contain second entry");
        }
    }

    @Test
    void xreadMultipleStreams() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            // Populate two streams
            client.getOutputStream().write(resp("XADD", "xread-ms-1", "1-0", "a", "1").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xread-ms-1", "2-0", "b", "2").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XADD", "xread-ms-2", "3-0", "c", "3").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xread-ms-2", "4-0", "d", "4").getBytes());
            in.read(buffer);

            // Read from both: entries after 1-0 from xread-ms-1, entries after 3-0 from xread-ms-2
            client.getOutputStream().write(resp("XREAD", "STREAMS", "xread-ms-1", "xread-ms-2", "1-0", "3-0").getBytes());
            String response = new String(buffer, 0, in.read(buffer));

            assertTrue(response.startsWith("*2\r\n"), "Expected 2 streams, got: " + response);
            assertTrue(response.contains("xread-ms-1"), "Should contain first key");
            assertTrue(response.contains("xread-ms-2"), "Should contain second key");
            assertTrue(response.contains("2-0"), "Should contain xread-ms-1 entry after 1-0");
            assertTrue(response.contains("4-0"), "Should contain xread-ms-2 entry after 3-0");
            assertFalse(response.contains("1-0\r\n"), "Should NOT contain excluded 1-0");
            assertFalse(response.contains("3-0\r\n"), "Should NOT contain excluded 3-0");
        }
    }

    @Test
    void xreadDollarIgnoresExistingEntries() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("XADD", "xread-dollar-1", "1-0", "a", "1").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xread-dollar-1", "2-0", "b", "2").getBytes());
            in.read(buffer);

            // $ = only new entries after this point; nothing new → empty/null response
            client.getOutputStream().write(resp("XREAD", "BLOCK", "100", "STREAMS", "xread-dollar-1", "$").getBytes());
            client.setSoTimeout(500);
            String response = new String(buffer, 0, in.read(buffer));
            assertEquals("*-1\r\n", response, "Expected null array for no new entries, got: " + response);
        }
    }

    @Test
    void xreadDollarBlocksAndReceivesNewEntry() throws Exception {
        try (Socket blocker = new Socket("localhost", PORT);
             Socket pusher  = new Socket("localhost", PORT)) {

            // Pre-populate the stream
            pusher.getOutputStream().write(resp("XADD", "xread-dollar-2", "1-0", "old", "data").getBytes());
            pusher.getInputStream().read(new byte[1024]);

            // Blocker subscribes with $ — should NOT receive 1-0
            blocker.getOutputStream().write(resp("XREAD", "BLOCK", "2000", "STREAMS", "xread-dollar-2", "$").getBytes());

            // Wait, then push a new entry
            Thread.sleep(200);
            pusher.getOutputStream().write(resp("XADD", "xread-dollar-2", "2-0", "new", "data").getBytes());
            pusher.getInputStream().read(new byte[1024]);

            byte[] buffer = new byte[4096];
            String response = new String(buffer, 0, blocker.getInputStream().read(buffer));

            assertTrue(response.contains("2-0"), "Should receive the new entry 2-0, got: " + response);
            assertFalse(response.contains("1-0"), "Should NOT receive the old entry 1-0");
        }
    }

    @Test
    void xrangeWithDashStartReturnsFromBeginning() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];

            client.getOutputStream().write(resp("XADD", "xrange-dash", "1-0", "a", "1").getBytes());
            in.read(buffer);
            client.getOutputStream().write(resp("XADD", "xrange-dash", "2-0", "b", "2").getBytes());
            in.read(buffer);

            client.getOutputStream().write(resp("XRANGE", "xrange-dash", "-", "2-0").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            assertTrue(response.startsWith("*2\r\n"), "Expected 2 entries, got: " + response);
            assertTrue(response.contains("1-0"), "Should contain first entry");
            assertTrue(response.contains("2-0"), "Should contain second entry");
        }
    }

    @Test
    void xrangeOnMissingKeyReturnsEmptyArray() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            client.getOutputStream().write(resp("XRANGE", "xrange-missing", "0-0", "9-9").getBytes());
            byte[] buffer = new byte[1024];
            assertEquals("*0\r\n", new String(buffer, 0, client.getInputStream().read(buffer)));
        }
    }

    @Test
    void xaddFullWildcardGeneratedIdIsUsedForSubsequentValidation() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("XADD", "xadd-fw-2", "*", "foo", "bar").getBytes());
            String response = new String(buffer, 0, in.read(buffer));
            String generatedId = response.split("\r\n")[1];

            // Explicit ID equal to the generated one must be rejected
            client.getOutputStream().write(resp("XADD", "xadd-fw-2", generatedId, "baz", "qux").getBytes());
            String error = new String(buffer, 0, in.read(buffer));
            assertTrue(error.startsWith("-ERR"), "Expected error for duplicate ID, got: " + error);
        }
    }

    @Test
    void xaddFullWildcardTwoConsecutiveIdsAreOrdered() throws Exception {
        try (Socket client = new Socket("localhost", PORT)) {
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[1024];

            client.getOutputStream().write(resp("XADD", "xadd-fw-3", "*", "a", "1").getBytes());
            String first = new String(buffer, 0, in.read(buffer)).split("\r\n")[1];

            client.getOutputStream().write(resp("XADD", "xadd-fw-3", "*", "b", "2").getBytes());
            String second = new String(buffer, 0, in.read(buffer)).split("\r\n")[1];

            String[] fp = first.split("-");
            String[] sp = second.split("-");
            long firstMillis = Long.parseLong(fp[0]), firstSeq = Long.parseLong(fp[1]);
            long secondMillis = Long.parseLong(sp[0]), secondSeq = Long.parseLong(sp[1]);

            boolean ordered = secondMillis > firstMillis ||
                    (secondMillis == firstMillis && secondSeq > firstSeq);
            assertTrue(ordered, "Second ID must be greater than first: " + first + " vs " + second);
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
