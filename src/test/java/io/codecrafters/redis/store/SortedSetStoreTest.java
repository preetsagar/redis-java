package io.codecrafters.redis.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SortedSetStoreTest {

    private SortedSetStore store;

    @BeforeEach
    void setUp() {
        store = new SortedSetStore();
    }

    @Test
    void addingANewMemberReturnsOne() {
        assertEquals(1, store.add("racers", 8.0, "Sam"));
    }

    @Test
    void reAddingAnExistingMemberReturnsZero() {
        store.add("racers", 8.0, "Sam");
        assertEquals(0, store.add("racers", 9.5, "Sam"));
    }

    @Test
    void distinctMembersEachCountAsNew() {
        assertEquals(1, store.add("racers", 6.1, "Ford"));
        assertEquals(1, store.add("racers", 8.2, "Royce"));
    }

    @Test
    void rankOrdersByScoreThenLexicographically() {
        store.add("z", 1.0, "member_with_score_1");
        store.add("z", 2.0, "member_with_score_2");
        store.add("z", 2.0, "another_member_with_score_2");

        assertEquals(0, store.rank("z", "member_with_score_1"));
        assertEquals(1, store.rank("z", "another_member_with_score_2")); // tie broken lexicographically
        assertEquals(2, store.rank("z", "member_with_score_2"));
    }

    @Test
    void rankIsNullForAMissingMemberOrKey() {
        store.add("z", 1.0, "a");
        assertNull(store.rank("z", "missing"));
        assertNull(store.rank("missing", "a"));
    }

    @Test
    void rangeReturnsMembersInRankOrderInclusive() {
        store.add("z", 8.1, "Sam-Bodden");
        store.add("z", 10.2, "Royce");
        store.add("z", 6.0, "Ford");
        store.add("z", 14.1, "Prickett");

        assertEquals(List.of("Ford", "Sam-Bodden", "Royce"), store.range("z", 0, 2));
    }

    @Test
    void rangeClampsStopAndReturnsEmptyWhenOutOfRange() {
        store.add("z", 1, "a");
        store.add("z", 2, "b");

        assertEquals(List.of("a", "b"), store.range("z", 0, 99)); // stop clamped to last
        assertEquals(List.of(), store.range("z", 5, 9));          // start past the end
        assertEquals(List.of(), store.range("z", 1, 0));          // start > stop
        assertEquals(List.of(), store.range("missing", 0, 10));   // missing set
    }
}