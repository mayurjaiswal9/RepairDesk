package com.acetelecom.repairdesk.feature.location.service;

import com.acetelecom.repairdesk.feature.location.domain.LocationFlag;

/**
 * Decides the flag for one fix against the saved shop pin.
 * A fix is trustworthy when accuracy is present and no wider than the shop radius.
 */
public final class AttendanceClassifier {

    private AttendanceClassifier() {
    }

    public record Fix(Double latitude, Double longitude, Double accuracyM, boolean mocked) {
    }

    public record Outcome(LocationFlag flag, Integer distanceMetres, boolean allowed) {
    }

    public static Outcome classify(Fix fix, Double shopLat, Double shopLng, int radiusM, boolean allowOutside) {
        boolean hasPoint = fix != null && fix.latitude() != null && fix.longitude() != null;
        Integer distance = hasPoint && shopLat != null && shopLng != null
                ? (int) Math.round(GeoMath.metresBetween(
                        fix.latitude(), fix.longitude(), shopLat, shopLng))
                : null;

        if (fix != null && fix.mocked()) {
            return new Outcome(LocationFlag.MOCKED, distance, true);
        }
        if (!hasPoint) {
            return new Outcome(LocationFlag.MISSING, null, true);
        }
        boolean trustworthy = fix.accuracyM() != null && fix.accuracyM() <= radiusM;
        if (!trustworthy || shopLat == null || shopLng == null) {
            return new Outcome(LocationFlag.UNCERTAIN, distance, true);
        }
        if (GeoMath.withinMetres(fix.latitude(), fix.longitude(), shopLat, shopLng, radiusM)) {
            return new Outcome(LocationFlag.INSIDE, distance, true);
        }
        if (allowOutside) {
            return new Outcome(LocationFlag.OUTSIDE, distance, true);
        }
        return new Outcome(LocationFlag.OUTSIDE, distance, false);
    }
}
