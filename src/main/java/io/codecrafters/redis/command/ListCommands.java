package io.codecrafters.redis.command;

import io.codecrafters.redis.protocol.RespEncoder;
import io.codecrafters.redis.store.ListStore;

import java.util.List;

public class ListCommands extends CommandGroup {

    public ListCommands(ListStore listStore) {
        add("RPUSH", args ->
                RespEncoder.respInteger(listStore.rightPush(args.get(1), args.subList(2, args.size()))));

        add("LPUSH", args ->
                RespEncoder.respInteger(listStore.leftPush(args.get(1), args.subList(2, args.size()))));

        add("LRANGE", args -> {
            List<String> result = listStore.lRange(
                    args.get(1), Integer.parseInt(args.get(2)), Integer.parseInt(args.get(3)));
            return RespEncoder.encodeList(result);
        });

        add("LLEN", args -> RespEncoder.respInteger(listStore.dataSize(args.get(1))));

        add("LPOP", args -> {
            if (args.size() == 2) {
                String value = listStore.leftPop(args.get(1));
                return value != null ? RespEncoder.bulkString(value) : RespEncoder.nullBulkString();
            }
            List<String> values = listStore.leftPop(args.get(1), Integer.parseInt(args.get(2)));
            return RespEncoder.encodeList(values);
        });

        add("BLPOP", args -> {
            try {
                long timeoutMillis = (long) (Double.parseDouble(args.get(2)) * 1000);
                List<String> result = listStore.blockedLeftPop(args.get(1), timeoutMillis);
                return result.isEmpty() ? RespEncoder.emptyList() : RespEncoder.encodeList(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return RespEncoder.emptyList();
            }
        });
    }
}