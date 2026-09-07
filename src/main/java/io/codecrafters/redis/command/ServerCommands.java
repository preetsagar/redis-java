package io.codecrafters.redis.command;

import io.codecrafters.redis.ReplicationInfo;
import io.codecrafters.redis.protocol.RespEncoder;

public class ServerCommands extends CommandGroup {

    public ServerCommands(ReplicationInfo replication) {
        add("INFO", args -> RespEncoder.multiBulkString(
                "role:" + replication.role(),
                "master_replid:" + replication.replId(),
                "master_repl_offset:" + replication.replOffset()));

        add("REPLCONF", args -> RespEncoder.simpleString("OK"));

        add("PSYNC", args ->
                RespEncoder.simpleString("FULLRESYNC " + replication.replId() + " 0"));
    }
}