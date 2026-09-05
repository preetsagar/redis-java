package io.codecrafters.redis.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RespParser {

    private final BufferedReader in;

    public RespParser(BufferedReader in) {
        this.in = in;
    }

    // Returns the next command as a String[], or null if the connection is closed.
    public List<String> readCommand() throws IOException {
        String line = in.readLine();
        if (line == null || !line.startsWith("*")) return null;

        int count = Integer.parseInt(line.substring(1));
        List<String> args = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            in.readLine(); // skip $<len>
            args.add(in.readLine());
        }
        return args;
    }
}