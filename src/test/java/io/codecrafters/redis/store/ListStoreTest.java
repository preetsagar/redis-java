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

    @Test
    void lrangeReturnsAllElements() {
        listStore.rpush("mylist", List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), listStore.lRange("mylist", 0, 2));
    }

    @Test
    void lrangeReturnsSubset() {
        listStore.rpush("mylist", List.of("a", "b", "c", "d"));
        assertEquals(List.of("b", "c"), listStore.lRange("mylist", 1, 2));
    }

    @Test
    void lrangeIsInclusive() {
        listStore.rpush("mylist", List.of("a", "b", "c", "d", "e"));
        assertEquals(List.of("a", "b", "c", "d", "e"), listStore.lRange("mylist", 0, 4));
    }

    @Test
    void lrangeWithNegativeEndReturnsToLastElement() {
        listStore.rpush("mylist", List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), listStore.lRange("mylist", 0, -1));
    }

    @Test
    void lrangeWithEndBeyondSizeReturnsTillEnd() {
        listStore.rpush("mylist", List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), listStore.lRange("mylist", 0, 100));
    }

    @Test
    void lrangeOnMissingKeyReturnsEmpty() {
        assertEquals(List.of(), listStore.lRange("missing", 0, -1));
    }

    @Test
    void lrangeWithStartGreaterThanEndReturnsEmpty() {
        listStore.rpush("mylist", List.of("a", "b", "c"));
        assertEquals(List.of(), listStore.lRange("mylist", 3, 1));
    }
}