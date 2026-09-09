package io.codecrafters.redis.rdb;

import io.codecrafters.redis.store.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RdbReaderTest {

    @TempDir
    Path dir;

    @Test
    void loadsAPlainStringKey() throws IOException {
        Store store = new Store();
        RdbReader.loadInto(writeRdb(selectDb(0), resize(1, 0), stringKey("foo", "bar")), store);

        assertEquals("bar", store.get("foo"));
        assertEquals(Set.of("foo"), store.keys());
    }

    @Test
    void missingFileLeavesTheStoreEmpty() {
        Store store = new Store();
        RdbReader.loadInto(dir.resolve("does-not-exist.rdb"), store);
        assertTrue(store.keys().isEmpty());
    }

    @Test
    void skipsMetadataSelectDbAndResizeOpcodes() throws IOException {
        Store store = new Store();
        RdbReader.loadInto(writeRdb(
                metadata("redis-ver", "6.0.16"),
                selectDb(0),
                resize(2, 1),
                stringKey("alpha", "1"),
                stringKey("beta", "2")), store);

        assertEquals(Set.of("alpha", "beta"), store.keys());
    }

    @Test
    void loadsAKeyWithAFutureMillisecondExpiry() throws IOException {
        Store store = new Store();
        long future = System.currentTimeMillis() + 60_000;
        RdbReader.loadInto(writeRdb(expireMs(future), stringKey("temp", "v")), store);

        assertEquals("v", store.get("temp"));
    }

    @Test
    void aKeyWhoseExpiryHasPassedIsNotReturnedByKeys() throws IOException {
        Store store = new Store();
        RdbReader.loadInto(writeRdb(expireMs(System.currentTimeMillis() - 1_000), stringKey("stale", "v")), store);

        assertFalse(store.keys().contains("stale"));
        assertNull(store.get("stale"));
    }

    @Test
    void decodesAnIntegerEncodedValue() throws IOException {
        Store store = new Store();
        // 0xC0 0x7B  ->  the 8-bit integer 123
        RdbReader.loadInto(writeRdb(bytes(0x00), str("count"), bytes(0xC0, 0x7B)), store);

        assertEquals("123", store.get("count"));
    }

    // --- tiny RDB builder -------------------------------------------------

    private Path writeRdb(byte[]... body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("REDIS0011".getBytes(StandardCharsets.US_ASCII));
        for (byte[] part : body) {
            out.writeBytes(part);
        }
        out.write(0xFF);
        out.writeBytes(new byte[8]); // checksum — not validated
        Path file = dir.resolve("dump.rdb");
        Files.write(file, out.toByteArray());
        return file;
    }

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    /** String, assuming length < 64 (the 0b00 length encoding). */
    private static byte[] str(String s) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(raw.length);
        out.writeBytes(raw);
        return out.toByteArray();
    }

    private static byte[] selectDb(int index) {
        return bytes(0xFE, index);
    }

    private static byte[] resize(int keys, int withExpiry) {
        return bytes(0xFB, keys, withExpiry);
    }

    private static byte[] metadata(String key, String value) {
        return concat(bytes(0xFA), str(key), str(value));
    }

    private static byte[] stringKey(String key, String value) {
        return concat(bytes(0x00), str(key), str(value));
    }

    private static byte[] expireMs(long epochMillis) {
        byte[] le = new byte[8];
        for (int i = 0; i < 8; i++) {
            le[i] = (byte) ((epochMillis >> (8 * i)) & 0xFF);
        }
        return concat(bytes(0xFC), le);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }
}