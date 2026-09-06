package io.codecrafters.redis.store;

/**
 * Bundles the per-type keyspaces behind one handle so wiring code and command
 * modules don't have to thread three separate stores everywhere.
 */
public class Database {

    private final Store stringStore = new Store();
    private final ListStore listStore = new ListStore();
    private final StreamStore streamStore = new StreamStore();

    public Store stringStore() {
        return stringStore;
    }

    public ListStore listStore() {
        return listStore;
    }

    public StreamStore streamStore() {
        return streamStore;
    }
}