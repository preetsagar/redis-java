package io.codecrafters.redis.pubsub;

import io.codecrafters.redis.client.ClientSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide channel -> subscribers registry. One instance per server.
 *
 * <p>ponytail: no unsubscribe/disconnect cleanup yet — later stages add it.
 */
public class PubSub {

    private final Map<String, Set<ClientSession>> byChannel = new ConcurrentHashMap<>();

    public void subscribe(String channel, ClientSession subscriber) {
        byChannel.computeIfAbsent(channel, c -> ConcurrentHashMap.newKeySet()).add(subscriber);
    }

    public int subscriberCount(String channel) {
        Set<ClientSession> subs = byChannel.get(channel);
        return subs == null ? 0 : subs.size();
    }
}