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
        assertEquals(1, listStore.rightPush("mylist", List.of("orange")));
    }

    @Test
    void rpushAppendsAndReturnsSize() {
        listStore.rightPush("mylist", List.of("orange"));
        assertEquals(2, listStore.rightPush("mylist", List.of("mango")));
    }

    @Test
    void rpushCreatesNewListIfKeyAbsent() {
        assertEquals(1, listStore.rightPush("newlist", List.of("value")));
    }

    @Test
    void rpushMultipleValuesOnSameKey() {
        listStore.rightPush("mylist", List.of("a"));
        listStore.rightPush("mylist", List.of("b"));
        assertEquals(3, listStore.rightPush("mylist", List.of("c")));
    }

    @Test
    void rpushDifferentKeysAreIndependent() {
        listStore.rightPush("list1", List.of("a", "b"));
        assertEquals(1, listStore.rightPush("list2", List.of("x")));
    }

    @Test
    void rpushMultipleValuesInOneCall() {
        assertEquals(3, listStore.rightPush("mylist", List.of("a", "b", "c")));
    }

    @Test
    void lrangeReturnsAllElements() {
        listStore.rightPush("mylist", List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), listStore.lRange("mylist", 0, 2));
    }

    @Test
    void lrangeReturnsSubset() {
        listStore.rightPush("mylist", List.of("a", "b", "c", "d"));
        assertEquals(List.of("b", "c"), listStore.lRange("mylist", 1, 2));
    }

    @Test
    void lrangeIsInclusive() {
        listStore.rightPush("mylist", List.of("a", "b", "c", "d", "e"));
        assertEquals(List.of("a", "b", "c", "d", "e"), listStore.lRange("mylist", 0, 4));
    }

    @Test
    void lrangeWithNegativeEndReturnsToLastElement() {
        listStore.rightPush("mylist", List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), listStore.lRange("mylist", 0, -1));
    }

    @Test
    void lrangeWithEndBeyondSizeReturnsTillEnd() {
        listStore.rightPush("mylist", List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), listStore.lRange("mylist", 0, 100));
    }

    @Test
    void lrangeOnMissingKeyReturnsEmpty() {
        assertEquals(List.of(), listStore.lRange("missing", 0, -1));
    }

    @Test
    void lrangeWithStartGreaterThanEndReturnsEmpty() {
        listStore.rightPush("mylist", List.of("a", "b", "c"));
        assertEquals(List.of(), listStore.lRange("mylist", 3, 1));
    }

    @Test
    void lrangeNegativeStartAndEnd() {
        listStore.rightPush("mylist", List.of("a", "b", "c", "d", "e"));
        assertEquals(List.of("d", "e"), listStore.lRange("mylist", -2, -1));
    }

    @Test
    void lrangeAllExceptLastTwo() {
        listStore.rightPush("mylist", List.of("a", "b", "c", "d", "e"));
        assertEquals(List.of("a", "b", "c"), listStore.lRange("mylist", 0, -3));
    }

    @Test
    void lrangeNegativeStartOutOfRangeTreatedAsZero() {
        listStore.rightPush("mylist", List.of("a", "b", "c", "d", "e"));
        assertEquals(List.of("a", "b", "c", "d", "e"), listStore.lRange("mylist", -6, -1));
    }

    @Test
    void lpushSingleElementReturnsOne() {
        assertEquals(1, listStore.leftPush("mylist", List.of("blueberry")));
    }

    @Test
    void lpushPrependsToFront() {
        listStore.leftPush("mylist", List.of("blueberry"));
        listStore.leftPush("mylist", List.of("grape"));
        assertEquals(List.of("grape", "blueberry"), listStore.lRange("mylist", 0, -1));
    }

    @Test
    void lpushMultipleValuesInOneCallReversesOrder() {
        listStore.leftPush("mylist", List.of("blueberry"));
        listStore.leftPush("mylist", List.of("grape", "pear"));
        assertEquals(List.of("pear", "grape", "blueberry"), listStore.lRange("mylist", 0, -1));
    }

    @Test
    void lpushReturnsSizeAfterInsert() {
        listStore.leftPush("mylist", List.of("a"));
        assertEquals(3, listStore.leftPush("mylist", List.of("b", "c")));
    }

    @Test
    void lpushCreatesNewListIfKeyAbsent() {
        assertEquals(1, listStore.leftPush("newlist", List.of("x")));
    }
}