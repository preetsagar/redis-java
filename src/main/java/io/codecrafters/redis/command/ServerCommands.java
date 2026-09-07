package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;

public class ServerCommands extends CommandGroup{

    public ServerCommands() {
        add("INFO", args -> {
            return RespEncoder.bulkString("role:master");
        });
    }
}
