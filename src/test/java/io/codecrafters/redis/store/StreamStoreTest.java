package io.codecrafters.redis.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StreamStoreTest {

    private StreamStore streamStore;

    @BeforeEach
    void setUp() {
        streamStore = new StreamStore();
    }

    @Test
    void xaddReturnsGivenId() {
        String id = streamStore.xadd("mystream", "0-1", List.of("foo", "bar"));
        assertEquals("0-1", id);
    }

    @Test
    void xaddReturnsFullTimestampId() {
        String id = streamStore.xadd("mystream", "1526919030474-0", List.of("temperature", "36"));
        assertEquals("1526919030474-0", id);
    }

    @Test
    void xaddCreatesStreamIfAbsent() {
        assertFalse(streamStore.hasKey("newstream"));
        streamStore.xadd("newstream", "1-0", List.of("k", "v"));
        assertTrue(streamStore.hasKey("newstream"));
    }

    @Test
    void xaddMultipleEntriesOnSameKey() {
        streamStore.xadd("mystream", "1-0", List.of("a", "1"));
        streamStore.xadd("mystream", "2-0", List.of("b", "2"));
        assertTrue(streamStore.hasKey("mystream"));
    }

    @Test
    void xaddDifferentKeysAreIndependent() {
        streamStore.xadd("stream1", "1-0", List.of("x", "1"));
        assertFalse(streamStore.hasKey("stream2"));
    }

    @Test
    void hasKeyReturnsFalseForMissingKey() {
        assertFalse(streamStore.hasKey("missing"));
    }

    @Test
    void hasKeyReturnsTrueAfterXadd() {
        streamStore.xadd("s", "1-0", List.of("k", "v"));
        assertTrue(streamStore.hasKey("s"));
    }

    @Test
    void xaddWithMultipleFieldPairs() {
        String id = streamStore.xadd("mystream", "1-0", List.of("temperature", "36", "humidity", "95"));
        assertEquals("1-0", id);
    }
}