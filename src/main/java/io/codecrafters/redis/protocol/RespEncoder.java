package io.codecrafters.redis.protocol;

public class RespEncoder {

    public static byte[] simpleString(String str) {
        return ("+" + str + "\r\n").getBytes();
    }

    public static byte[] bulkString(String str) {
        return ("$" + str.length() + "\r\n" + str + "\r\n").getBytes();
    }

    public static byte[] nullBulkString() {
        return "$-1\r\n".getBytes();
    }

    public static byte[] error(String message) {
        return ("-ERR " + message + "\r\n").getBytes();
    }
}