package io.codecrafters.redis.command;

import io.codecrafters.redis.ReplicationInfo;
import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.rdb.Rdb;
import io.codecrafters.redis.replication.Replicas;

public class ServerCommands extends CommandGroup {

    public ServerCommands(ReplicationInfo replicationInfo, Replicas replicas) {
        add("INFO", args -> RespEncoder.multiBulkString(
                "role:" + replicationInfo.role(),
                "connected_slaves:" + replicas.count(),
                "master_replid:" + replicationInfo.replId(),
                "master_repl_offset:" + replicationInfo.replOffset()));

        add("REPLCONF", args -> RespEncoder.simpleString("OK"));

        // ponytail: single hardcoded "default" user until auth lands
        add("ACL", args -> switch (args.get(1).toUpperCase()) {
            case "WHOAMI" -> RespEncoder.bulkString("default");
            case "GETUSER" -> RespEncoder.array(RespEncoder.bulkString("flags"),
                    RespEncoder.array(RespEncoder.bulkString("nopass")));
            default -> RespEncoder.error("unknown ACL subcommand '" + args.get(1) + "'");
        });

        // +FULLRESYNC <replid> 0\r\n  immediately followed by  $<len>\r\n<rdb bytes>
        add("PSYNC", args -> RespEncoder.concat(
                RespEncoder.simpleString("FULLRESYNC " + replicationInfo.replId() + " 0"),
                RespEncoder.rdbFile(Rdb.EMPTY)));

        add("WAIT", args -> {
            int wanted = Integer.parseInt(args.get(1));
            long timeoutMillis = Long.parseLong(args.get(2));
            long target = replicationInfo.replOffset();
            int acked = target == 0
                    ? replicas.count()            // nothing written yet — every replica is trivially caught up
                    : replicas.waitForAcks(target, wanted, timeoutMillis);
            return RespEncoder.respInteger(acked);
        });
    }
}