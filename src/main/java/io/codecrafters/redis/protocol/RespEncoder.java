package io.codecrafters.redis.protocol;

import java.util.List;

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

    public static byte[] encodeList(List<String> arr) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(arr.size()).append("\r\n");
        for (String str : arr) {
            sb.append("$").append(str.length()).append("\r\n");
            sb.append(str).append("\r\n");
        }
        return sb.toString().getBytes();
    }

    public static byte[] error(String message) {
        return ("-ERR " + message + "\r\n").getBytes();
    }

    public static byte[] RespInteger(Integer value) {
        return (":"+value+"\r\n").getBytes();
    }
}