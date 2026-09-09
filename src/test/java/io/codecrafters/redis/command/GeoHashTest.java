package io.codecrafters.redis.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoHashTest {

    @Test
    void encodesKnownCitiesToTheirRedisScores() {
        assertEquals(3962257306574459L, GeoHash.encode(13.7220, 100.5252));  // Bangkok
        assertEquals(4069885364908765L, GeoHash.encode(39.9075, 116.3972));  // Beijing
        assertEquals(3673983964876493L, GeoHash.encode(52.5244, 13.4105));   // Berlin
        assertEquals(2163557714755072L, GeoHash.encode(51.5074, -0.1278));   // London
        assertEquals(1791873974549446L, GeoHash.encode(40.7128, -74.0060));  // New York
        assertEquals(3663832752681684L, GeoHash.encode(48.8534, 2.3488));    // Paris
        assertEquals(3252046221964352L, GeoHash.encode(-33.8688, 151.2093)); // Sydney
        assertEquals(4171231230197045L, GeoHash.encode(35.6895, 139.6917));  // Tokyo
    }

    @Test
    void decodesAKnownScore() {
        double[] pos = GeoHash.decode(3663832614298053L); // [lon, lat]
        assertEquals(2.2944715, pos[0], 1e-6);
        assertEquals(48.8584625, pos[1], 1e-6);
    }
}
