package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;

public class ConnectionCommands extends CommandGroup {

    public ConnectionCommands() {
        add("PING", args -> RespEncoder.simpleString("PONG"));
        add("ECHO", args -> RespEncoder.bulkString(args.get(1)));
    }
}