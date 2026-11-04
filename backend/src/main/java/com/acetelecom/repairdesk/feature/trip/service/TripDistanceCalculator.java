package com.acetelecom.repairdesk.feature.trip.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.acetelecom.repairdesk.feature.location.service.GeoMath;
import com.acetelecom.repairdesk.feature.trip.domain.TripEndReason;
import com.acetelecom.repairdesk.feature.trip.domain.TripFlag;

/** Trip distance and flags from GPS fixes. Gap distances come from the supplied {@link GapRouter}. */
public final class TripDistanceCalculator {

    static final double MAX_ACCURACY_M = 50d;
    static final double JITTER_M = 25d;
    static final double MAX_SPEED_MPS = 120_000d / 3_600d;
    static final double GAP_SECONDS = 300d;
    static final double GAP_METRES = 500d;
    static final double STRAIGHT_LINE_FACTOR = 1.3d;

    private TripDistanceCalculator() {
    }

    public record Fix(Instant recordedAt, double latitude, double longitude, Double accuracyM, boolean mocked) {
    }

    @FunctionalInterface
    public interface GapRouter {
        int metres(double fromLat, double fromLng, double toLat, double toLng);
    }

    public record Result(int trackedDistanceM, int estimatedGapM, int gapCount, int mockedPoints,
            List<Fix> accepted) {

        public int totalM() {
            return trackedDistanceM + estimatedGapM;
        }

        public Fix lastAccepted() {
            return accepted.isEmpty() ? null : accepted.get(accepted.size() - 1);
        }
    }

    /** Same figure as the Maps fallback, without calling Google. */
    public static int straightLineGap(double fromLat, double fromLng, double toLat, double toLng) {
        return (int) Math.round(GeoMath.metresBetween(fromLat, fromLng, toLat, toLng) * STRAIGHT_LINE_FACTOR);
    }

    /** {@code start} and {@code end} may be null. They stay first and last whatever their times. */
    public static Result calculate(Fix start, List<Fix> points, Fix end, GapRouter router) {
        List<Fix> ordered = new ArrayList<>();
        if (start != null) {
            ordered.add(start);
        }
        List<Fix> sorted = new ArrayList<>(points == null ? List.of() : points);
        sorted.sort(Comparator.comparing(Fix::recordedAt));
        ordered.addAll(sorted);
        if (end != null) {
            ordered.add(end);
        }

        double tracked = 0d;
        int gapMetres = 0;
        int gaps = 0;
        int mocked = 0;
        List<Fix> accepted = new ArrayList<>();
        Fix anchor = null;
        for (Fix fix : ordered) {
            if (fix.mocked()) {
                mocked++;
            }
            if (fix.accuracyM() != null && fix.accuracyM() > MAX_ACCURACY_M) {
                continue;
            }
            if (anchor == null) {
                anchor = fix;
                accepted.add(fix);
                continue;
            }
            double metres = GeoMath.metresBetween(anchor.latitude(), anchor.longitude(),
                    fix.latitude(), fix.longitude());
            if (metres < JITTER_M) {
                continue;
            }
            double seconds = Duration.between(anchor.recordedAt(), fix.recordedAt()).toMillis() / 1000d;
            if (metres / Math.max(seconds, 1d) > MAX_SPEED_MPS) {
                continue;
            }
            if (seconds > GAP_SECONDS && metres > GAP_METRES) {
                gapMetres += Math.max(0, router.metres(anchor.latitude(), anchor.longitude(),
                        fix.latitude(), fix.longitude()));
                gaps++;
            } else {
                tracked += metres;
            }
            anchor = fix;
            accepted.add(fix);
        }
        return new Result((int) Math.round(tracked), gapMetres, gaps, mocked, List.copyOf(accepted));
    }

    /**
     * Flags for an ended trip. {@code jobLat}/{@code jobLng} are the job pin and may be null.
     * FAR_FROM_JOB uses the last accepted fix.
     */
    public static Set<TripFlag> flags(Result result, Integer plannedDistanceM, TripEndReason endReason,
            Double jobLat, Double jobLng, int farFromAddressM) {
        Set<TripFlag> flags = EnumSet.noneOf(TripFlag.class);
        if (result.accepted().size() < 2) {
            flags.add(TripFlag.NO_POINTS);
        }
        int total = result.totalM();
        if (plannedDistanceM != null) {
            if (plannedDistanceM > 500 && total > 1.5d * plannedDistanceM) {
                flags.add(TripFlag.LONGER_THAN_ROUTE);
            }
            if (plannedDistanceM > 1000 && total < 0.5d * plannedDistanceM) {
                flags.add(TripFlag.SHORTER_THAN_ROUTE);
            }
        }
        if (result.mockedPoints() > 0) {
            flags.add(TripFlag.MOCKED);
        }
        if (result.gapCount() > 0) {
            flags.add(TripFlag.GAPS);
        }
        Fix last = result.lastAccepted();
        boolean arrivalReason = endReason == TripEndReason.ARRIVED
                || endReason == TripEndReason.GEOFENCE
                || endReason == TripEndReason.JOB_STARTED;
        if (arrivalReason && last != null && jobLat != null && jobLng != null
                && GeoMath.metresBetween(last.latitude(), last.longitude(), jobLat, jobLng) > farFromAddressM) {
            flags.add(TripFlag.FAR_FROM_JOB);
        }
        return flags;
    }
}
