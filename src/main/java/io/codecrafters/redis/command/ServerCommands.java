package io.codecrafters.redis.command;

import io.codecrafters.redis.ReplicationInfo;
import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.rdb.Rdb;
import io.codecrafters.redis.replication.Replicas;

public class ServerCommands extends CommandGroup {

    public ServerCommands(ReplicationInfo replication, Replicas replicas) {
        add("INFO", args -> RespEncoder.multiBulkString(
                "role:" + replication.role(),
                "connected_slaves:" + replicas.count(),
                "master_replid:" + replication.replId(),
                "master_repl_offset:" + replication.replOffset()));

        add("REPLCONF", args -> RespEncoder.simpleString("OK"));

        // +FULLRESYNC <replid> 0\r\n  immediately followed by  $<len>\r\n<rdb bytes>
        add("PSYNC", args -> RespEncoder.concat(
                RespEncoder.simpleString("FULLRESYNC " + replication.replId() + " 0"),
                RespEncoder.rdbFile(Rdb.EMPTY)));

        add("WAIT", args -> RespEncoder.respInteger(replicas.count()));
    }
}