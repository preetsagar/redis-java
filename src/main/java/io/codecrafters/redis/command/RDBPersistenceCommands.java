package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.rdb.Rdb;

import java.util.List;

public class RDBPersistenceCommands extends CommandGroup {

    public RDBPersistenceCommands(Rdb redisDataBase) {
        // CONFIG GET <param>  ->  ["<param>", "<value>"]
        add("CONFIG", args -> {
            String param = args.get(2);
            if (param.equalsIgnoreCase("dir")) {
                return RespEncoder.encodeList(List.of("dir", redisDataBase.getDir()));
            }
            if (param.equalsIgnoreCase("dbfilename")) {
                return RespEncoder.encodeList(List.of("dbfilename", redisDataBase.getDbFileName()));
            }
            return RespEncoder.encodeList(List.of());
        });
    }
}