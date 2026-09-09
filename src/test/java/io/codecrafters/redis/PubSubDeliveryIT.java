package io.codecrafters.redis;

import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PUBLISH delivers {@code ["message", channel, payload]} to every client
 * subscribed to that channel — and to no one else.
 */
class PubSubDeliveryIT extends RedisServerTestBase {

    @Test
    void publishedMessagesReachOnlyTheChannelsSubscribers() throws Exception {
        try (Socket sub1 = new Socket("localhost", PORT);
             Socket sub2 = new Socket("localhost", PORT);
             Socket other = new Socket("localhost", PORT);
             Socket publisher = new Socket("localhost", PORT)) {

            sub1.setSoTimeout(2000);
            sub2.setSoTimeout(2000);
            other.setSoTimeout(500);

            subscribe(sub1, "foo");
            subscribe(sub2, "foo");
            subscribe(other, "bar");

            publisher.getOutputStream().write(resp("PUBLISH", "foo", "hello").getBytes());
            assertEquals(":2\r\n", new String(publisher.getInputStream().readNBytes(4)));

            String message = "*3\r\n$7\r\nmessage\r\n$3\r\nfoo\r\n$5\r\nhello\r\n";
            assertEquals(message, new String(sub1.getInputStream().readNBytes(message.length())));
            assertEquals(message, new String(sub2.getInputStream().readNBytes(message.length())));

            // 'other' is subscribed to bar, not foo — it must receive nothing
            assertThrows(SocketTimeoutException.class, () -> other.getInputStream().read());
        }
    }

    @Test
    void anUnsubscribedClientStopsReceivingChannelMessages() throws Exception {
        try (Socket sub1 = new Socket("localhost", PORT);
             Socket sub2 = new Socket("localhost", PORT);
             Socket publisher = new Socket("localhost", PORT)) {

            sub1.setSoTimeout(500);
            sub2.setSoTimeout(2000);

            subscribe(sub1, "foo");
            subscribe(sub2, "foo");

            sub1.getOutputStream().write(resp("UNSUBSCRIBE", "foo").getBytes());
            sub1.getInputStream().read(new byte[64]); // consume the unsubscribe reply

            publisher.getOutputStream().write(resp("PUBLISH", "foo", "after").getBytes());
            assertEquals(":1\r\n", new String(publisher.getInputStream().readNBytes(4)));

            String message = "*3\r\n$7\r\nmessage\r\n$3\r\nfoo\r\n$5\r\nafter\r\n";
            assertEquals(message, new String(sub2.getInputStream().readNBytes(message.length())));
            assertThrows(SocketTimeoutException.class, () -> sub1.getInputStream().read());
        }
    }

    private void subscribe(Socket client, String channel) throws Exception {
        client.getOutputStream().write(resp("SUBSCRIBE", channel).getBytes());
        client.getInputStream().read(new byte[64]); // consume the subscribe confirmation
    }
}