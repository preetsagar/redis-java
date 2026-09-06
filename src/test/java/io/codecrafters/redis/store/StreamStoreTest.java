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

    @Test
    void xaddDifferentKeysHaveIndependentLastIds() {
        streamStore.xadd("stream1", "5-0", List.of("k", "v"));
        // stream2 can start from 1-0 independently
        String id = streamStore.xadd("stream2", "1-0", List.of("k", "v"));
        assertEquals("1-0", id);
    }

    @Test
    void xaddRejectsSameId() {
        streamStore.xadd("mystream", "1-1", List.of("foo", "bar"));
        assertThrows(IllegalArgumentException.class,
                () -> streamStore.xadd("mystream", "1-1", List.of("bar", "baz")));
    }

    @Test
    void xaddRejectsSmallerMillis() {
        streamStore.xadd("mystream", "1-1", List.of("foo", "bar"));
        assertThrows(IllegalArgumentException.class,
                () -> streamStore.xadd("mystream", "0-2", List.of("bar", "baz")));
    }

    @Test
    void xaddRejectsZeroZeroId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> streamStore.xadd("mystream", "0-0", List.of("foo", "bar")));
        assertTrue(ex.getMessage().contains("0-0"));
    }

    @Test
    void xaddMinimumValidIdIsZeroOne() {
        String id = streamStore.xadd("mystream", "0-1", List.of("foo", "bar"));
        assertEquals("0-1", id);
    }

    @Test
    void xaddAcceptsGreaterSequenceWithSameMillis() {
        streamStore.xadd("mystream", "1-1", List.of("a", "1"));
        String id = streamStore.xadd("mystream", "1-2", List.of("b", "2"));
        assertEquals("1-2", id);
    }

    // Wildcard sequence: "<millis>-*"

    @Test
    void xaddWildcardSequenceOnEmptyStreamStartsAtZero() {
        String id = streamStore.xadd("mystream", "5-*", List.of("foo", "bar"));
        assertEquals("5-0", id);
    }

    @Test
    void xaddWildcardSequenceIncrementsWhenMillisMatches() {
        streamStore.xadd("mystream", "5-3", List.of("a", "1"));
        String id = streamStore.xadd("mystream", "5-*", List.of("b", "2"));
        assertEquals("5-4", id);
    }

    @Test
    void xaddWildcardSequenceResetsToZeroWhenMillisIncreases() {
        streamStore.xadd("mystream", "0-1", List.of("a", "1"));
        String id = streamStore.xadd("mystream", "1-*", List.of("b", "2"));
        assertEquals("1-0", id);
    }

    @Test
    void xaddWildcardZeroMillisOnEmptyStreamProducesZeroOne() {
        // "0-*" on empty stream → last is "0-0" → same millis → seq = 0+1 = 1
        String id = streamStore.xadd("mystream", "0-*", List.of("foo", "bar"));
        assertEquals("0-1", id);
    }

    @Test
    void xaddWildcardSequenceAfterMultipleEntries() {
        streamStore.xadd("mystream", "3-0", List.of("a", "1"));
        streamStore.xadd("mystream", "3-1", List.of("b", "2"));
        String id = streamStore.xadd("mystream", "3-*", List.of("c", "3"));
        assertEquals("3-2", id);
    }

    @Test
    void xaddWildcardIdIsStoredAndNextExplicitMustBeGreater() {
        String id = streamStore.xadd("mystream", "2-*", List.of("a", "1")); // generates 2-0
        assertEquals("2-0", id);
        assertThrows(IllegalArgumentException.class,
                () -> streamStore.xadd("mystream", "2-0", List.of("b", "2")));
    }

    @Test
    void xaddWildcardDifferentKeysAreIndependent() {
        streamStore.xadd("s1", "5-3", List.of("a", "1"));
        // s2 is empty, so "5-*" should produce "5-0"
        String id = streamStore.xadd("s2", "5-*", List.of("b", "2"));
        assertEquals("5-0", id);
    }

    // Full wildcard: "*"

    @Test
    void xaddFullWildcardReturnsMillisAndZeroSequence() {
        long before = System.currentTimeMillis();
        String id = streamStore.xadd("mystream", "*", List.of("foo", "bar"));
        long after = System.currentTimeMillis();

        String[] parts = id.split("-");
        assertEquals(2, parts.length);
        long millis = Long.parseLong(parts[0]);
        long seq = Long.parseLong(parts[1]);

        assertTrue(millis >= before && millis <= after, "Millis should be current time");
        assertEquals(0, seq);
    }

    @Test
    void xaddFullWildcardIncrementsSequenceIfSameMillis() {
        String first = streamStore.xadd("mystream", "*", List.of("a", "1"));
        long firstMillis = Long.parseLong(first.split("-")[0]);

        // Force same-millis scenario by using partial wildcard with same timestamp
        String second = streamStore.xadd("mystream", firstMillis + "-*", List.of("b", "2"));
        long secondSeq = Long.parseLong(second.split("-")[1]);
        assertEquals(1, secondSeq);
    }

    @Test
    void xaddFullWildcardGeneratedIdIsStoredForValidation() {
        String id = streamStore.xadd("mystream", "*", List.of("foo", "bar"));
        String[] parts = id.split("-");
        long millis = Long.parseLong(parts[0]);
        long seq = Long.parseLong(parts[1]);

        // Explicit ID equal to the generated one must be rejected
        assertThrows(IllegalArgumentException.class,
                () -> streamStore.xadd("mystream", millis + "-" + seq, List.of("baz", "qux")));
    }

    @Test
    void xaddFullWildcardDifferentKeysAreIndependent() {
        streamStore.xadd("s1", "*", List.of("a", "1"));
        // s2 should generate its own ID independently
        String id = streamStore.xadd("s2", "*", List.of("b", "2"));
        assertNotNull(id);
        assertTrue(id.contains("-"));
    }

    // XRANGE

    @Test
    void xrangeReturnsAllEntries() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        streamStore.xadd("s", "2-0", List.of("b", "2"));
        streamStore.xadd("s", "3-0", List.of("c", "3"));
        var result = streamStore.xrange("s", "1-0", "3-0");
        assertEquals(3, result.size());
        assertEquals("1-0", result.get(0).id());
        assertEquals("3-0", result.get(2).id());
    }

    @Test
    void xrangeReturnsSubset() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        streamStore.xadd("s", "2-0", List.of("b", "2"));
        streamStore.xadd("s", "3-0", List.of("c", "3"));
        var result = streamStore.xrange("s", "2-0", "3-0");
        assertEquals(2, result.size());
        assertEquals("2-0", result.get(0).id());
    }

    @Test
    void xrangeIsInclusive() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        streamStore.xadd("s", "2-0", List.of("b", "2"));
        var result = streamStore.xrange("s", "1-0", "2-0");
        assertEquals(2, result.size());
    }

    @Test
    void xrangeStartWithoutSequenceDefaultsToZero() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        streamStore.xadd("s", "1-1", List.of("b", "2"));
        // "1" without seq → start = 1-0
        var result = streamStore.xrange("s", "1", "1-1");
        assertEquals(2, result.size());
    }

    @Test
    void xrangeEndWithoutSequenceDefaultsToMax() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        streamStore.xadd("s", "1-5", List.of("b", "2"));
        // "1" without seq → end = 1-MAX → includes all with millis 1
        var result = streamStore.xrange("s", "1-0", "1");
        assertEquals(2, result.size());
    }

    @Test
    void xrangeExcludesOutOfRangeEntries() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        streamStore.xadd("s", "2-0", List.of("b", "2"));
        streamStore.xadd("s", "3-0", List.of("c", "3"));
        var result = streamStore.xrange("s", "2-0", "2-0");
        assertEquals(1, result.size());
        assertEquals("2-0", result.get(0).id());
    }

    @Test
    void xrangeOnMissingKeyReturnsEmpty() {
        var result = streamStore.xrange("missing", "0-0", "9-9");
        assertEquals(0, result.size());
    }

    // XREAD

    @Test
    void xreadReturnsEntriesAfterGivenId() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        streamStore.xadd("s", "2-0", List.of("b", "2"));
        streamStore.xadd("s", "3-0", List.of("c", "3"));
        var result = streamStore.xread("s", "1-0");
        assertEquals(2, result.size());
        assertEquals("2-0", result.get(0).id());
        assertEquals("3-0", result.get(1).id());
    }

    @Test
    void xreadIsExclusive() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        var result = streamStore.xread("s", "1-0");
        assertEquals(0, result.size());
    }

    @Test
    void xreadFromZeroReturnsAll() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        streamStore.xadd("s", "2-0", List.of("b", "2"));
        var result = streamStore.xread("s", "0-0");
        assertEquals(2, result.size());
    }

    @Test
    void xreadOnMissingKeyReturnsEmpty() {
        var result = streamStore.xread("missing", "0-0");
        assertEquals(0, result.size());
    }

    @Test
    void xreadEntryFieldsArePreserved() {
        streamStore.xadd("s", "1-0", List.of("temperature", "36", "humidity", "95"));
        streamStore.xadd("s", "2-0", List.of("temperature", "37"));
        var result = streamStore.xread("s", "1-0");
        assertEquals(List.of("temperature", "37"), result.get(0).fields());
    }

    @Test
    void xrangeWithDashStartReturnsFromBeginning() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        streamStore.xadd("s", "2-0", List.of("b", "2"));
        var result = streamStore.xrange("s", "-", "2-0");
        assertEquals(2, result.size());
        assertEquals("1-0", result.get(0).id());
    }

    @Test
    void xrangeWithPlusEndReturnsToEnd() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        streamStore.xadd("s", "2-0", List.of("b", "2"));
        var result = streamStore.xrange("s", "1-0", "+");
        assertEquals(2, result.size());
        assertEquals("2-0", result.get(1).id());
    }

    @Test
    void xrangeWithDashAndPlusReturnsAll() {
        streamStore.xadd("s", "1-0", List.of("a", "1"));
        streamStore.xadd("s", "2-0", List.of("b", "2"));
        streamStore.xadd("s", "3-0", List.of("c", "3"));
        var result = streamStore.xrange("s", "-", "+");
        assertEquals(3, result.size());
    }

    @Test
    void xrangeEntryFieldsArePreserved() {
        streamStore.xadd("s", "1-0", List.of("temperature", "36", "humidity", "95"));
        var result = streamStore.xrange("s", "1-0", "1-0");
        assertEquals(List.of("temperature", "36", "humidity", "95"), result.get(0).fields());
    }
}