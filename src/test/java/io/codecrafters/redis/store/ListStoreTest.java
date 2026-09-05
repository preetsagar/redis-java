package io.codecrafters.redis.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListStoreTest {

    private ListStore listStore;

    @BeforeEach
    void setUp() {
        listStore = new ListStore();
    }

    @Test
    void rpushReturnsOneForFirstInsert() {
        assertEquals(1, listStore.rpush("mylist", "orange"));
    }

    @Test
    void rpushAppendsAndReturnsSize() {
        listStore.rpush("mylist", "orange");
        assertEquals(2, listStore.rpush("mylist", "mango"));
    }

    @Test
    void rpushCreatesNewListIfKeyAbsent() {
        assertEquals(1, listStore.rpush("newlist", "value"));
    }

    @Test
    void rpushMultipleValuesOnSameKey() {
        listStore.rpush("mylist", "a");
        listStore.rpush("mylist", "b");
        assertEquals(3, listStore.rpush("mylist", "c"));
    }

    @Test
    void rpushDifferentKeysAreIndependent() {
        listStore.rpush("list1", "a");
        listStore.rpush("list1", "b");
        assertEquals(1, listStore.rpush("list2", "x"));
    }
}