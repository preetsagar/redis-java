package io.codecrafters.redis.client;

import io.codecrafters.redis.store.Store;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-connection state: whether a MULTI is open, the queued commands, and the
 * key versions snapshotted by WATCH. One instance per client thread, so the
 * transaction fields need no synchronization; the WATCH dirty check only reads
 * the (thread-safe) version counters in {@link Store}.
 */
public class ClientSession {

    private final Store store;
    private final OutputStream connection; // null for socket-free tests

    private boolean inMulti = false;
    private final List<List<String>> commandQueue = new ArrayList<>();
    // Key -> version snapshotted at WATCH time. EXEC aborts if any current version differs.
    private final Map<String, Long> watchedVersions = new HashMap<>();

    public ClientSession(Store store, OutputStream connection) {
        this.store = store;
        this.connection = connection;
    }

    public boolean inMulti() {
        return inMulti;
    }

    public void beginMulti() {
        inMulti = true;
    }

    public void endMulti() {
        inMulti = false;
    }

    public void queue(List<String> args) {
        commandQueue.add(args);
    }

    /** Returns the queued commands and empties the queue. */
    public List<List<String>> drainQueue() {
        List<List<String>> drained = new ArrayList<>(commandQueue);
        commandQueue.clear();
        return drained;
    }

    public void clearQueue() {
        commandQueue.clear();
    }

    public void watch(String key) {
        watchedVersions.put(key, store.versionOf(key));
    }

    public void clearWatches() {
        watchedVersions.clear();
    }

    public boolean isAnyWatchedKeyDirty() {
        return watchedVersions.entrySet().stream()
                .anyMatch(e -> store.versionOf(e.getKey()) != e.getValue());
    }

    // --- pub/sub ---

    private final Set<String> channels = new LinkedHashSet<>();

    /** Subscribes to {@code channel} and returns the total channel count for this client. */
    public int subscribe(String channel) {
        channels.add(channel);
        return channels.size();
    }

    /** True once this client holds at least one subscription (restricted command set applies). */
    public boolean inSubscribedMode() {
        return !channels.isEmpty();
    }

    /** Pushes a message to this client's socket (used by PUBLISH from another thread). */
    public void deliver(byte[] message) {
        if (connection == null) {
            return;
        }
        try {
            synchronized (connection) {
                connection.write(message);
                connection.flush();
            }
        } catch (IOException gone) {
            // subscriber disconnected — cleanup is a later stage
        }
    }
}