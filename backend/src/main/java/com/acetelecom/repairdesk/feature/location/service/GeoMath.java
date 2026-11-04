package com.acetelecom.repairdesk.feature.location.service;

/** Distance helpers. Coordinates are WGS84 degrees. */
public final class GeoMath {

    private static final double EARTH_METRES = 6_371_000d;

    private GeoMath() {
    }

    public static double metresBetween(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_METRES * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static boolean withinMetres(double lat1, double lng1, double lat2, double lng2, double radiusMetres) {
        return metresBetween(lat1, lng1, lat2, lng2) <= radiusMetres;
    }
}
