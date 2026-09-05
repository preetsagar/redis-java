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

}