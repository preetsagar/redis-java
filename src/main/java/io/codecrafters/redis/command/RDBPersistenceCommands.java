package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.rdb.Rdb;

import java.util.List;
import java.util.Map;

import static io.codecrafters.redis.Main.getParsed;

public class RDBPersistenceCommands extends CommandGroup {

    public RDBPersistenceCommands(Rdb redisDataBase) {
        // dir/dbfilename come from Rdb (already flag-aware); AOF options fall back
        // to their defaults when the matching --flag is absent.
        Map<String, String> config = Map.of(
                "dir", redisDataBase.getDir(),
                "dbfilename", redisDataBase.getDbFileName(),
                "appendonly", getParsed().getOrDefault("appendonly", "no"),
                "appenddirname", getParsed().getOrDefault("appenddirname", "appendonlydir"),
                "appendfilename", getParsed().getOrDefault("appendfilename", "appendonly.aof"),
                "appendfsync", getParsed().getOrDefault("appendfsync", "everysec"));

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