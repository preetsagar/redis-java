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
}