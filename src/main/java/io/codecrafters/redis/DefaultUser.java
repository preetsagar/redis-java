package io.codecrafters.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * The single {@code default} ACL user. Starts with {@code nopass}; adding a
 * password (ACL SETUSER {@code >pw}) stores its SHA-256 hash and clears nopass.
 * One instance per server, shared across connections.
 */
public final class DefaultUser {

    private boolean nopass = true;
    private final List<String> passwordHashes = new ArrayList<>();

    public synchronized void addPassword(String plaintext) {
        passwordHashes.add(sha256Hex(plaintext));
        nopass = false;
    }

    public synchronized boolean nopass() {
        return nopass;
    }

    public synchronized List<String> passwordHashes() {
        return List.copyOf(passwordHashes);
    }

    /** True if {@code plaintext} authenticates: nopass, or its hash is one of the stored ones. */
    public synchronized boolean authenticates(String plaintext) {
        return nopass || passwordHashes.contains(sha256Hex(plaintext));
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is always present
        }
    }
}
