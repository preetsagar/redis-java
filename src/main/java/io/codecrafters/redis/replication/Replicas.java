package io.codecrafters.redis.replication;

import io.codecrafters.redis.protocol.RespEncoder;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The replica connections a master is propagating writes to. Each {@link Handle}
 * is the output side of the socket a replica opened for its {@code PSYNC}
 * handshake, plus the offset that replica last acknowledged.
 *
 * <p>Registered by {@code ClientHandler} after it answers {@code PSYNC}; written
 * to by any client thread that runs a write command; the acked offsets feed the
 * {@code WAIT} command.
 */
public class Replicas {

    /** One replica link: where to write, and the offset it last acknowledged. */
    public static final class Handle {
        private final OutputStream out;
        private volatile long ackedOffset;

        private Handle(OutputStream out) {
            this.out = out;
        }
    }

    private final List<Handle> handles = new CopyOnWriteArrayList<>();
    private final Object ackMonitor = new Object();

    public Handle register(OutputStream link) {
        Handle handle = new Handle(link);
        handles.add(handle);
        return handle;
    }

    public int count() {
        return handles.size();
    }

    /** Records an offset a replica reported via {@code REPLCONF ACK} and wakes any pending WAIT. */
    public void recordAck(Handle handle, long offset) {
        handle.ackedOffset = offset;
        synchronized (ackMonitor) {
            ackMonitor.notifyAll();
        }
    }

    /** Sends the already-RESP-encoded command to every replica; drops any that fail. */
    public void propagate(byte[] command) {
        for (Handle handle : handles) {
            try {
                synchronized (handle.out) {
                    handle.out.write(command);
                    handle.out.flush();
                }
            } catch (IOException disconnected) {
                handles.remove(handle);
            }
        }
    }

    /**
     * Asks every replica for its offset ({@code REPLCONF GETACK *}), then blocks
     * up to {@code timeoutMillis} for at least {@code wanted} of them to report an
     * offset &gt;= {@code targetOffset}. Returns how many have reached it (which
     * may be fewer or more than {@code wanted}).
     */
    public int waitForAcks(long targetOffset, int wanted, long timeoutMillis) {
        propagate(RespEncoder.encodeList(List.of("REPLCONF", "GETACK", "*")));

        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (ackMonitor) {
            while (true) {
                int acked = countAckedAtLeast(targetOffset);
                long remaining = deadline - System.currentTimeMillis();
                if (acked >= wanted || remaining <= 0) {
                    return acked;
                }
                try {
                    ackMonitor.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return countAckedAtLeast(targetOffset);
                }
            }
        }
    }

    private int countAckedAtLeast(long offset) {
        int reached = 0;
        for (Handle handle : handles) {
            if (handle.ackedOffset >= offset) {
                reached++;
            }
        }
        return reached;
    }
}