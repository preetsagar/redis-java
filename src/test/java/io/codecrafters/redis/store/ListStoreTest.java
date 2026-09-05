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

    @Test
    void llenMissingKeyReturnsZero() {
        assertEquals(0, listStore.dataSize("missing"));
    }

    @Test
    void llenAfterRpush() {
        listStore.rightPush("mylist", List.of("a", "b", "c"));
        assertEquals(3, listStore.dataSize("mylist"));
    }

    @Test
    void llenAfterLpush() {
        listStore.leftPush("mylist", List.of("a", "b"));
        assertEquals(2, listStore.dataSize("mylist"));
    }

    @Test
    void llenAfterMixedPushes() {
        listStore.rightPush("mylist", List.of("a", "b"));
        listStore.leftPush("mylist", List.of("c"));
        assertEquals(3, listStore.dataSize("mylist"));
    }

    @Test
    void lpopReturnsFirstElement() {
        listStore.rightPush("mylist", List.of("a", "b", "c"));
        assertEquals("a", listStore.leftPop("mylist"));
    }

    @Test
    void lpopRemovesElement() {
        listStore.rightPush("mylist", List.of("a", "b", "c"));
        listStore.leftPop("mylist");
        assertEquals(List.of("b", "c"), listStore.lRange("mylist", 0, -1));
    }

    @Test
    void lpopOnEmptyListReturnsNull() {
        listStore.rightPush("mylist", List.of("a"));
        listStore.leftPop("mylist");
        assertNull(listStore.leftPop("mylist"));
    }

    @Test
    void lpopOnMissingKeyReturnsNull() {
        assertNull(listStore.leftPop("missing"));
    }

    @Test
    void lpopDecreasesSize() {
        listStore.rightPush("mylist", List.of("a", "b", "c"));
        listStore.leftPop("mylist");
        assertEquals(2, listStore.dataSize("mylist"));
    }

    @Test
    void lpopWithCountReturnsElements() {
        listStore.rightPush("mylist", List.of("a", "b", "c", "d"));
        assertEquals(List.of("a", "b"), listStore.leftPop("mylist", 2));
    }

    @Test
    void lpopWithCountRemovesElements() {
        listStore.rightPush("mylist", List.of("a", "b", "c"));
        listStore.leftPop("mylist", 2);
        assertEquals(List.of("c"), listStore.lRange("mylist", 0, -1));
    }

    @Test
    void lpopWithCountGreaterThanSizeReturnsAll() {
        listStore.rightPush("mylist", List.of("a", "b"));
        assertEquals(List.of("a", "b"), listStore.leftPop("mylist", 10));
    }

    @Test
    void lpopWithCountOnMissingKeyReturnsEmpty() {
        assertEquals(List.of(), listStore.leftPop("missing", 3));
    }

    @Test
    void blpopReturnsImmediatelyIfElementExists() throws InterruptedException {
        listStore.rightPush("mylist", List.of("foobar"));
        List<String> result = listStore.blockedLeftPop("mylist", 1000);
        assertEquals(List.of("mylist", "foobar"), result);
    }

    @Test
    void blpopTimesOutOnEmptyList() throws InterruptedException {
        long start = System.currentTimeMillis();
        List<String> result = listStore.blockedLeftPop("empty", 100);
        long elapsed = System.currentTimeMillis() - start;
        assertEquals(List.of(), result);
        assertTrue(elapsed >= 100, "Should have waited at least 100ms");
    }

    @Test
    void blpopBlocksAndReceivesElementWhenPushed() throws InterruptedException {
        Thread pusher = new Thread(() -> {
            try {
                Thread.sleep(100);
                listStore.rightPush("mylist", List.of("hello"));
            } catch (InterruptedException ignored) {}
        });
        pusher.start();

        List<String> result = listStore.blockedLeftPop("mylist", 2000);
        assertEquals(List.of("mylist", "hello"), result);
        pusher.join();
    }

    @Test
    void blpopRemovesElementFromList() throws InterruptedException {
        listStore.rightPush("mylist", List.of("a", "b"));
        listStore.blockedLeftPop("mylist", 1000);
        assertEquals(1, listStore.dataSize("mylist"));
    }
}