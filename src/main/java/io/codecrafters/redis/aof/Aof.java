package io.codecrafters.redis.aof;

import io.codecrafters.redis.client.ClientSession;
import io.codecrafters.redis.command.CommandDispatcher;
import io.codecrafters.redis.protocol.RespParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.SYNC;

/**
 * Append-only file. On {@link #open} it lays out {@code <dir>/<appenddirname>/}
 * (creating the first incremental file + manifest only if they're absent — the
 * tester may pre-create them), then reads the manifest to find the active
 * {@code type i} file. {@link #append} writes each write command, RESP-encoded,
 * to that file; with {@code appendfsync always} it forces the bytes to disk
 * before returning.
 *
 * <p>ponytail: opens the file per append — keep a channel open if AOF throughput
 * ever matters.
 */
public final class Aof {

    private static final Aof DISABLED = new Aof(null, false);

    private final Path file;
    private final boolean fsyncAlways;
    private boolean replaying;

    private Aof(Path file, boolean fsyncAlways) {
        this.file = file;
        this.fsyncAlways = fsyncAlways;
    }

    /** True while {@link #replay} is applying the file — write side effects are suppressed then. */
    public boolean isReplaying() {
        return replaying;
    }

    /** Replays the active AOF file's RESP commands through the dispatcher to rebuild state. */
    public void replay(CommandDispatcher dispatcher) {
        if (file == null || Files.notExists(file)) {
            return;
        }
        replaying = true;
        try (BufferedReader in = Files.newBufferedReader(file)) {
            RespParser parser = new RespParser(in);
            ClientSession session = dispatcher.newSession();
            List<String> args;
            while ((args = parser.readCommand()) != null) {
                dispatcher.dispatch(args, session);
            }
        } catch (IOException e) {
            System.out.println("[aof] replay failed: " + e.getMessage());
        } finally {
            replaying = false;
        }
    }

    public static Aof disabled() {
        return DISABLED;
    }

    public static Aof open(Map<String, String> flags, String dir) {
        if (!"yes".equals(flags.get("appendonly"))) {
            return DISABLED;
        }
        Path appendDir = Path.of(dir, flags.getOrDefault("appenddirname", "appendonlydir"));
        String base = flags.getOrDefault("appendfilename", "appendonly.aof");
        Path manifest = appendDir.resolve(base + ".manifest");
        try {
            Files.createDirectories(appendDir);
            if (Files.notExists(manifest)) {
                Files.write(appendDir.resolve(base + ".1.incr.aof"), new byte[0]);
                Files.writeString(manifest, "file " + base + ".1.incr.aof seq 1 type i\n");
            }
            Path active = appendDir.resolve(activeIncrFile(manifest, base));
            return new Aof(active, "always".equals(flags.get("appendfsync")));
        } catch (IOException e) {
            System.out.println("[aof] setup failed: " + e.getMessage());
            return DISABLED;
        }
    }

    /** The last {@code file <name> seq <n> type i} entry in the manifest. */
    private static String activeIncrFile(Path manifest, String fallbackBase) throws IOException {
        String active = fallbackBase + ".1.incr.aof";
        for (String line : Files.readAllLines(manifest)) {
            String[] t = line.trim().split(" ");
            if (t.length >= 6 && t[0].equals("file") && t[4].equals("type") && t[5].equals("i")) {
                active = t[1];
            }
        }
        return active;
    }

    public void append(byte[] command) {
        if (file == null) {
            return;
        }
        try {
            if (fsyncAlways) {
                Files.write(file, command, CREATE, APPEND, SYNC);
            } else {
                Files.write(file, command, CREATE, APPEND);
            }
        } catch (IOException e) {
            System.out.println("[aof] append failed: " + e.getMessage());
        }
    }
}