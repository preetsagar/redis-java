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
    private static final double EARTH_RADIUS_M = 6372797.560856; // the exact value Redis uses

    private GeoHash() {
    }

    /** Metres per one unit of a GEO distance unit (m / km / mi / ft). */
    static double unitToMetres(String unit) {
        return switch (unit.toLowerCase()) {
            case "km" -> 1000.0;
            case "mi" -> 1609.34;
            case "ft" -> 0.3048;
            default -> 1.0; // m
        };
    }

    /** Haversine great-circle distance between two lat/lon points, in metres. */
    static double distance(double lat1, double lon1, double lat2, double lon2) {
        double lat1r = Math.toRadians(lat1);
        double lat2r = Math.toRadians(lat2);
        double u = Math.sin((lat2r - lat1r) / 2);
        double v = Math.sin(Math.toRadians(lon2 - lon1) / 2);
        double a = u * u + Math.cos(lat1r) * Math.cos(lat2r) * v * v;
        return 2.0 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    static long encode(double latitude, double longitude) {
        int latInt = (int) (GRID * (latitude - MIN_LATITUDE) / LATITUDE_RANGE);
        int lonInt = (int) (GRID * (longitude - MIN_LONGITUDE) / LONGITUDE_RANGE);
        return spread(latInt) | (spread(lonInt) << 1);
    }

    /** Reverses {@link #encode}: score -> {@code [longitude, latitude]} (grid-cell centre). */
    static double[] decode(long score) {
        int gridLat = compact(score);
        int gridLon = compact(score >> 1);
        double latitude = midpoint(MIN_LATITUDE, LATITUDE_RANGE, gridLat);
        double longitude = midpoint(MIN_LONGITUDE, LONGITUDE_RANGE, gridLon);
        return new double[]{longitude, latitude};
    }

    private static double midpoint(double min, double range, int gridNumber) {
        double lo = min + range * (gridNumber / GRID);
        double hi = min + range * ((gridNumber + 1) / GRID);
        return (lo + hi) / 2;
    }

    private static int compact(long v) {
        v = v & 0x5555555555555555L;
        v = (v | (v >> 1)) & 0x3333333333333333L;
        v = (v | (v >> 2)) & 0x0F0F0F0F0F0F0F0FL;
        v = (v | (v >> 4)) & 0x00FF00FF00FF00FFL;
        v = (v | (v >> 8)) & 0x0000FFFF0000FFFFL;
        v = (v | (v >> 16)) & 0x00000000FFFFFFFFL;
        return (int) v;
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
