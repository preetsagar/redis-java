package io.codecrafters.redis.command;

import java.util.HashMap;
import java.util.Map;

/**
 * A group of related commands. Subclasses register their handlers in the
 * constructor via {@link #add}; the registry collects {@link #commands()}.
 */
public abstract class CommandGroup {

    private final Map<String, Command> commands = new HashMap<>();

    protected void add(String commandName, Command command) {
        commands.put(commandName, command);
    }

    public Map<String, Command> commands() {
        return commands;
    }
}