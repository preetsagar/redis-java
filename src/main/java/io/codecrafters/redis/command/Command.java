package io.codecrafters.redis.command;

import java.util.List;

/**
 * A single data command (SET, LPUSH, XADD, ...). {@code args} includes the
 * command name at index 0. Returns the RESP-encoded reply. Connection/session
 * commands (MULTI, EXEC, WATCH, ...) are not Commands — they live in
 * {@link CommandDispatcher} because they touch per-connection state.
 */
@FunctionalInterface
public interface Command {
    byte[] execute(List<String> args);
}