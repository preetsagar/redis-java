package io.codecrafters.redis;

/**
 * A server's replication identity — the data behind {@code INFO replication} and
 * the {@code PSYNC} reply. One instance per {@link RedisServer}, so a master and
 * a replica can run in the same JVM without stepping on each other.
 */
public class ReplicationInfo {

    private static final String DEFAULT_REPLID = "8371b4fb1155b71f4a04d3e1bc3e18c4a990aeeb";

    private final String role;
    private final String replId;
    private volatile long replOffset;

    public ReplicationInfo(String role) {
        this.role = role;
        this.replId = DEFAULT_REPLID;
        this.replOffset = 0L;
    }

    public String role() {
        return role;
    }

    public String replId() {
        return replId;
    }

    public long replOffset() {
        return replOffset;
    }
}