package io.codecrafters.redis.rdb;

import io.codecrafters.redis.store.Store;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a Redis RDB file and loads its string keys (with expiries) into a
 * {@link Store}. Only what this challenge needs: the header, metadata /
 * select-db / resize-db opcodes are skipped, string values (type {@code 0x00})
 * are loaded, and {@code FC}/{@code FD} expiries are applied.
 */
public final class RdbReader {

    private RdbReader() {
    }

    private static final int OP_EXPIRE_MS = 0xFC;
    private static final int OP_EXPIRE_S = 0xFD;
    private static final int OP_SELECT_DB = 0xFE;
    private static final int OP_RESIZE_DB = 0xFB;
    private static final int OP_METADATA = 0xFA;
    private static final int OP_EOF = 0xFF;
    private static final int TYPE_STRING = 0x00;

    /** No-op if {@code file} is null or doesn't exist — the database is then just empty. */
    public static void loadInto(Path file, Store store) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            parse(in, store);
        } catch (IOException e) {
            System.out.println("[rdb] could not read " + file + ": " + e.getMessage());
        }
    }

    private static void parse(InputStream in, Store store) throws IOException {
        byte[] header = in.readNBytes(9); // "REDIS" + 4-char version
        if (header.length < 9 || !"REDIS".equals(new String(header, 0, 5, StandardCharsets.US_ASCII))) {
            return;
        }

        Long pendingExpiry = null;
        int op;
        while ((op = in.read()) != -1 && op != OP_EOF) {
            switch (op) {
                case OP_METADATA -> {
                    readString(in);
                    readString(in);
                }
                case OP_SELECT_DB -> readLength(in);
                case OP_RESIZE_DB -> {
                    readLength(in);
                    readLength(in);
                }
                case OP_EXPIRE_MS -> pendingExpiry = readUnsignedLE(in, 8);
                case OP_EXPIRE_S -> pendingExpiry = readUnsignedLE(in, 4) * 1000L;
                case TYPE_STRING -> {
                    String key = readString(in);
                    String value = readString(in);
                    store.load(key, value, pendingExpiry);
                    pendingExpiry = null;
                }
                default -> throw new IOException("unsupported RDB opcode/type 0x" + Integer.toHexString(op));
            }
        }
    }

    /** Little-endian unsigned integer of {@code byteCount} bytes. */
    private static long readUnsignedLE(InputStream in, int byteCount) throws IOException {
        long value = 0;
        for (int i = 0; i < byteCount; i++) {
            value |= (long) readByte(in) << (8 * i);
        }
        return value;
    }

    /** A length-encoded value; the 0b11 (special) case isn't valid where a plain length is expected. */
    private static long readLength(InputStream in) throws IOException {
        int first = readByte(in);
        return switch ((first & 0xC0) >> 6) {
            case 0 -> first & 0x3F;
            case 1 -> ((long) (first & 0x3F) << 8) | readByte(in);
            case 2 -> readUnsignedBE(in, 4);
            default -> throw new IOException("special length encoding where a plain length was expected");
        };
    }

    private static long readUnsignedBE(InputStream in, int byteCount) throws IOException {
        long value = 0;
        for (int i = 0; i < byteCount; i++) {
            value = (value << 8) | readByte(in);
        }
        return value;
    }

    private static String readString(InputStream in) throws IOException {
        int first = readByte(in);
        int type = (first & 0xC0) >> 6;

        if (type == 3) { // integer-as-string encodings
            return switch (first) {
                case 0xC0 -> Integer.toString((byte) readByte(in));               // 8-bit
                case 0xC1 -> Integer.toString((short) readUnsignedLE(in, 2));      // 16-bit LE
                case 0xC2 -> Integer.toString((int) readUnsignedLE(in, 4));        // 32-bit LE
                default -> throw new IOException("unsupported string encoding 0x" + Integer.toHexString(first));
            };
        }

        long length = switch (type) {
            case 0 -> first & 0x3F;
            case 1 -> ((long) (first & 0x3F) << 8) | readByte(in);
            default -> readUnsignedBE(in, 4); // type == 2
        };
        byte[] bytes = in.readNBytes((int) length);
        if (bytes.length != length) {
            throw new EOFException("truncated string (wanted " + length + " bytes)");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b == -1) {
            throw new EOFException();
        }
        return b;
    }
}