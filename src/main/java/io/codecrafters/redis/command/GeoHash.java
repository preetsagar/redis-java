package io.codecrafters.redis.command;

/**
 * Redis's geo encoding: interleave the 26-bit grid indices of latitude and
 * longitude into a single 52-bit score (see the challenge's geocoding repo).
 */
final class GeoHash {

    private static final double MIN_LATITUDE = -85.05112878;
    private static final double MAX_LATITUDE = 85.05112878;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;
    private static final double LATITUDE_RANGE = MAX_LATITUDE - MIN_LATITUDE;
    private static final double LONGITUDE_RANGE = MAX_LONGITUDE - MIN_LONGITUDE;
    private static final double GRID = Math.pow(2, 26);

    private GeoHash() {
    }

    static long encode(double latitude, double longitude) {
        int latInt = (int) (GRID * (latitude - MIN_LATITUDE) / LATITUDE_RANGE);
        int lonInt = (int) (GRID * (longitude - MIN_LONGITUDE) / LONGITUDE_RANGE);
        return spread(latInt) | (spread(lonInt) << 1);
    }

    private static long spread(int v) {
        long result = v & 0xFFFFFFFFL;
        result = (result | (result << 16)) & 0x0000FFFF0000FFFFL;
        result = (result | (result << 8)) & 0x00FF00FF00FF00FFL;
        result = (result | (result << 4)) & 0x0F0F0F0F0F0F0F0FL;
        result = (result | (result << 2)) & 0x3333333333333333L;
        result = (result | (result << 1)) & 0x5555555555555555L;
        return result;
    }
}
