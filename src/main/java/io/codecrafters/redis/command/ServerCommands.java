package io.codecrafters.redis.command;

import io.codecrafters.redis.RedisServer;
import io.codecrafters.redis.protocol.RespEncoder;

public class ServerCommands extends CommandGroup{

    public ServerCommands() {
        add("INFO", args ->
                RespEncoder.multiBulkString("role:"+RedisServer.getRole(),
                        "master_replid:"+RedisServer.getMaster_replid(),
                        "master_repl_offset:"+RedisServer.getMaster_repl_offset())
        );
    }
}
