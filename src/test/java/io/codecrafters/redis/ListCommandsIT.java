package io.codecrafters.redis;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class ListCommandsIT extends RedisServerTestBase {

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
}