package io.codecrafters.redis.command;

import io.codecrafters.redis.DefaultUser;
import io.codecrafters.redis.ReplicationInfo;
import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.rdb.Rdb;
import io.codecrafters.redis.replication.Replicas;

public class ServerCommands extends CommandGroup {

    public ServerCommands(ReplicationInfo replicationInfo, Replicas replicas, DefaultUser user) {
        add("INFO", args -> RespEncoder.multiBulkString(
                "role:" + replicationInfo.role(),
                "connected_slaves:" + replicas.count(),
                "master_replid:" + replicationInfo.replId(),
                "master_repl_offset:" + replicationInfo.replOffset()));

        add("REPLCONF", args -> RespEncoder.simpleString("OK"));

        // ponytail: single "default" user; auth enforcement lands later
        add("ACL", args -> switch (args.get(1).toUpperCase()) {
            case "WHOAMI" -> RespEncoder.bulkString("default");
            case "SETUSER" -> {
                for (String rule : args.subList(3, args.size())) {
                    if (rule.startsWith(">")) {
                        user.addPassword(rule.substring(1));
                    }
                }
                yield RespEncoder.simpleString("OK");
            }
            case "GETUSER" -> {
                byte[] flags = user.nopass()
                        ? RespEncoder.array(RespEncoder.bulkString("nopass"))
                        : RespEncoder.emptyArray();
                byte[] passwords = RespEncoder.array(user.passwordHashes().stream()
                        .map(RespEncoder::bulkString).toArray(byte[][]::new));
                yield RespEncoder.array(RespEncoder.bulkString("flags"), flags,
                        RespEncoder.bulkString("passwords"), passwords);
            }
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