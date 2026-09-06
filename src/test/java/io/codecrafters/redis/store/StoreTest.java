package io.codecrafters.redis.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoreTest {

    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
    }

    @Test
    void setAndGetValue() {
        store.set("foo", "bar");
        assertEquals("bar", store.get("foo"));
    }

    @Test
    void getMissingKeyReturnsNull() {
        assertNull(store.get("missing"));
    }

    @Test
    void overwriteExistingKey() {
        store.set("foo", "bar");
        store.set("foo", "baz");
        assertEquals("baz", store.get("foo"));
    }

    @Test
    void setWithTtlReturnsValueBeforeExpiry() {
        store.set("foo", "bar", 5000);
        assertEquals("bar", store.get("foo"));
    }

    @Test
    void setWithTtlReturnsNullAfterExpiry() throws InterruptedException {
        store.set("foo", "bar", 50);
        Thread.sleep(100);
        assertNull(store.get("foo"));
    }

    @Test
    void expiredKeyIsRemovedOnGet() throws InterruptedException {
        store.set("foo", "bar", 50);
        Thread.sleep(100);
        store.get("foo");
        store.set("foo", "fresh");
        assertEquals("fresh", store.get("foo"));
    }

    @Test
    void overwriteWithNoTtlClearsPreviousTtl() throws InterruptedException {
        store.set("foo", "bar", 50);
        store.set("foo", "baz");
        Thread.sleep(100);
        assertEquals("baz", store.get("foo"));
    }

    // INCR

    @Test
    void incrementExistingIntegerKey() {
        store.set("counter", "5");
        assertEquals("6", store.increment("counter"));
    }

    @Test
    void incrementMissingKeyStartsFromZero() {
        assertEquals("1", store.increment("missing"));
    }

    @Test
    void incrementMultipleTimesAccumulates() {
        store.set("counter", "0");
        store.increment("counter");
        store.increment("counter");
        assertEquals("3", store.increment("counter"));
    }

    @Test
    void incrementUpdatesStoredValue() {
        store.set("counter", "10");
        store.increment("counter");
        assertEquals("11", store.get("counter"));
    }

    @Test
    void incrementNonIntegerThrowsException() {
        store.set("counter", "notanumber");
        assertThrows(NumberFormatException.class, () -> store.increment("counter"));
    }

}