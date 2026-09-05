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
        assertEquals(1, listStore.rpush("mylist", List.of("orange")));
    }

    @Test
    void rpushAppendsAndReturnsSize() {
        listStore.rpush("mylist", List.of("orange"));
        assertEquals(2, listStore.rpush("mylist", List.of("mango")));
    }

    @Test
    void rpushCreatesNewListIfKeyAbsent() {
        assertEquals(1, listStore.rpush("newlist", List.of("value")));
    }

    @Test
    void rpushMultipleValuesOnSameKey() {
        listStore.rpush("mylist", List.of("a"));
        listStore.rpush("mylist", List.of("b"));
        assertEquals(3, listStore.rpush("mylist", List.of("c")));
    }

    @Test
    void rpushDifferentKeysAreIndependent() {
        listStore.rpush("list1", List.of("a", "b"));
        assertEquals(1, listStore.rpush("list2", List.of("x")));
    }

    @Test
    void rpushMultipleValuesInOneCall() {
        assertEquals(3, listStore.rpush("mylist", List.of("a", "b", "c")));
    }
}