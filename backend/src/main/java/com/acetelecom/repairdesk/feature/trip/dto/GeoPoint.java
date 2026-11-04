package com.acetelecom.repairdesk.feature.trip.dto;

public record GeoPoint(double latitude, double longitude) {

    public static GeoPoint of(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return new GeoPoint(latitude, longitude);
    }
}
