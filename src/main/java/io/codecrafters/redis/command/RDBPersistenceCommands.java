package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.rdb.Rdb;

import java.util.List;
import java.util.Map;

public class RDBPersistenceCommands extends CommandGroup {

    public RDBPersistenceCommands(Rdb redisDataBase) {
        Map<String, String> config = Map.of(
                "dir", redisDataBase.getDir(),
                "dbfilename", redisDataBase.getDbFileName(),
                "appendonly", "no",
                "appenddirname", "appendonlydir",
                "appendfilename", "appendonly.aof",
                "appendfsync", "everysec");

        // CONFIG GET <param>  ->  ["<param>", "<value>"]
        add("CONFIG", args -> {
            String param = args.get(2).toLowerCase();
            String value = config.get(param);
            return value != null
                    ? RespEncoder.encodeList(List.of(param, value))
                    : RespEncoder.encodeList(List.of());
        });
    }
}