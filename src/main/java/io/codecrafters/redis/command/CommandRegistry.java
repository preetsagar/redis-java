package io.codecrafters.redis.command;

import io.codecrafters.redis.store.Database;

import java.util.HashMap;
import java.util.Map;

/**
 * Name -> handler lookup for all data commands, assembled from the command
 * groups. Transaction/connection control (MULTI, EXEC, WATCH, ...) is handled
 * by {@link CommandDispatcher}, not here.
 */
public class CommandRegistry {

    private final Map<String, Command> commands = new HashMap<>();

    public CommandRegistry(Database db) {
        register(new ConnectionCommands());
        register(new StringCommands(db.stringStore()));
        register(new ListCommands(db.listStore()));
        register(new StreamCommands(db.streamStore()));
        register(new KeyCommands(db));
        register(new ServerCommands());
    }

    private void register(CommandGroup group) {
        commands.putAll(group.commands());
    }

    /** Returns the handler for {@code commandName} (already upper-cased), or null if unknown. */
    public Command get(String commandName) {
        return commands.get(commandName);
    }
}