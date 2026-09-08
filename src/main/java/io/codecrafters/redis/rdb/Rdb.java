package io.codecrafters.redis.rdb;

import java.util.HexFormat;

/**
 * RDB config + the canned empty snapshot.
 *
 * <ul>
 *   <li>{@link #EMPTY} — a minimal valid RDB payload a master sends after
 *       {@code +FULLRESYNC} so a replica can complete a full resync.</li>
 *   <li>{@code dir} / {@code dbFileName} — where the on-disk RDB lives, from
 *       {@code --dir} / {@code --dbfilename}; surfaced by {@code CONFIG GET} and
 *       used to load keys at startup.</li>
 * </ul>
 */
public final class Rdb {

    // "REDIS0011" magic, redis-ver / redis-bits aux fields, EOF opcode, CRC64.
    private static final String EMPTY_RDB_HEX =
            "524544495330303131"                 // REDIS0011
            + "fa0972656469732d76657205372e322e30" // aux: redis-ver = 7.2.0
            + "fa0a72656469732d62697473c040"       // aux: redis-bits = 64
            + "ff"                                 // EOF opcode
            + "f06e3bfec0ff5aa2";                  // CRC64 of the above

    public static final byte[] EMPTY = HexFormat.of().parseHex(EMPTY_RDB_HEX);

    private final String dir;
    private final String dbFileName;

    public Rdb() {
        this(null, null);
    }

    public Rdb(String dbFileName, String dir) {
        this.dbFileName = dbFileName != null ? dbFileName : "dump.rdb";
        this.dir = dir != null ? dir : ".";
    }

    public String getDir() {
        return dir;
    }

    public String getDbFileName() {
        return dbFileName;
    }
}