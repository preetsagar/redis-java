package io.codecrafters.redis.protocol;

import io.codecrafters.redis.store.StreamStore;
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

    public static byte[] emptyList() {
        return "*-1\r\n".getBytes();
    }
    public static byte[] emptyArray() {
        return "*0\r\n".getBytes();
    }

    public static byte[] error(String message) {
        return ("-ERR " + message + "\r\n").getBytes();
    }

    public static byte[] respInteger(Integer value) {
        return (":"+value+"\r\n").getBytes();
    }

    public static byte[] encodeXRead(List<String> keys, List<List<StreamStore.StreamEntry>> allEntries) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(keys.size()).append("\r\n");
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            List<StreamStore.StreamEntry> entries = allEntries.get(i);
            sb.append("*2\r\n");
            sb.append("$").append(key.length()).append("\r\n").append(key).append("\r\n");
            sb.append("*").append(entries.size()).append("\r\n");
            for (StreamStore.StreamEntry entry : entries) {
                sb.append("*2\r\n");
                sb.append("$").append(entry.id().length()).append("\r\n").append(entry.id()).append("\r\n");
                List<String> fields = entry.fields();
                sb.append("*").append(fields.size()).append("\r\n");
                for (String field : fields) {
                    sb.append("$").append(field.length()).append("\r\n").append(field).append("\r\n");
                }
            }
        }
        return sb.toString().getBytes();
    }

    public static byte[] encodeStreamEntries(List<StreamStore.StreamEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(entries.size()).append("\r\n");
        for (StreamStore.StreamEntry entry : entries) {
            sb.append("*2\r\n");
            sb.append("$").append(entry.id().length()).append("\r\n").append(entry.id()).append("\r\n");
            List<String> fields = entry.fields();
            sb.append("*").append(fields.size()).append("\r\n");
            for (String field : fields) {
                sb.append("$").append(field.length()).append("\r\n").append(field).append("\r\n");
            }
        }
        return sb.toString().getBytes();
    }
}