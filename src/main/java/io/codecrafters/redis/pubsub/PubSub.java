package io.codecrafters.redis.pubsub;

import io.codecrafters.redis.client.ClientSession;
import io.codecrafters.redis.protocol.RespEncoder;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide channel -> subscribers registry. One instance per server, shared
 * by every connection thread.
 *
 * <p>ponytail: no unsubscribe/disconnect cleanup yet — later stages add it.
 */
public class PubSub {

    private final Map<String, Set<ClientSession>> byChannel = new ConcurrentHashMap<>();

    public void subscribe(String channel, ClientSession subscriber) {
        byChannel.computeIfAbsent(channel, c -> ConcurrentHashMap.newKeySet()).add(subscriber);
    }

    /** Delivers {@code ["message", channel, payload]} to every subscriber; returns how many. */
    public int publish(String channel, String payload) {
        Set<ClientSession> subs = byChannel.getOrDefault(channel, Set.of());
        byte[] message = RespEncoder.concat("*3\r\n".getBytes(),
                RespEncoder.bulkString("message"),
                RespEncoder.bulkString(channel),
                RespEncoder.bulkString(payload));
        for (ClientSession sub : subs) {
            sub.deliver(message);
        }
        return subs.size();
    }
}