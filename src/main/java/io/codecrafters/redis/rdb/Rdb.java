package io.codecrafters.redis.rdb;

import java.util.HexFormat;

/**
 * Minimal RDB support. For now just a canned empty RDB, sent by a master after
 * {@code +FULLRESYNC} so a replica can complete a full resync. Reading/writing
 * real RDB files is a separate challenge extension.
 */
public final class Rdb {
    private String dbFileName;
    private String dir;

    public Rdb(String dbFileName, String dir) {
        this.dbFileName = dbFileName != null ? dbFileName : "dump.rdb";
        this.dir        = dir        != null ? dir        : "/Users/preetsagar/Desktop/prep/reddis/codecrafters-redis-java";
    }

    // "REDIS0011" magic, redis-ver / redis-bits aux fields, EOF opcode, CRC64.
    private static final String EMPTY_RDB_HEX =
            "524544495330303131"                 // REDIS0011
            + "fa0972656469732d76657205372e322e30" // aux: redis-ver = 7.2.0
            + "fa0a72656469732d62697473c040"       // aux: redis-bits = 64
            + "ff"                                 // EOF opcode
            + "f06e3bfec0ff5aa2";                  // CRC64 of the above

    public static final byte[] EMPTY = HexFormat.of().parseHex(EMPTY_RDB_HEX);

    public Rdb() {
        this.dbFileName = "dump.rdb";
        this.dir = "Users/preetsagar/Desktop/prep/reddis/codecrafters-redis-java";
    }

    public String getDbFileName() {
        return dbFileName;
    }

    public String getDir() {
        return dir;
    }
}