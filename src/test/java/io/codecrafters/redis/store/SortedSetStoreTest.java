package io.codecrafters.redis.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}