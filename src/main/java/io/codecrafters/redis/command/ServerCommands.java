package io.codecrafters.redis.command;

import io.codecrafters.redis.RedisServer;
import io.codecrafters.redis.protocol.RespEncoder;

public class ServerCommands extends CommandGroup{

    public ServerCommands() {
        add("INFO", args -> RespEncoder.bulkString("role:"+RedisServer.getRole()));
    }
}
