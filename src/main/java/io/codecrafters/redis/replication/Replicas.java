package io.codecrafters.redis.replication;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The replica connections a master is propagating writes to. Each entry is the
 * output side of the socket a replica opened for its {@code PSYNC} handshake.
 * Registered by {@code ClientHandler} after it answers {@code PSYNC}; written to
 * by any client thread that runs a write command.
 */
public class Replicas {

    private final List<OutputStream> links = new CopyOnWriteArrayList<>();

    public void register(OutputStream link) {
        links.add(link);
    }

    public int count() {
        return links.size();
    }

    /** Sends the already-RESP-encoded command to every replica; drops any that fail. */
    public void propagate(byte[] command) {
        for (OutputStream link : links) {
            try {
                synchronized (link) {
                    link.write(command);
                    link.flush();
                }
            } catch (IOException disconnected) {
                links.remove(link);
            }
        }
    }
}