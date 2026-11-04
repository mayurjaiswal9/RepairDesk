package com.acetelecom.repairdesk.feature.trip.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Douglas–Peucker on [latitude, longitude] pairs, tolerance in metres. */
public final class RouteSimplifier {

    public static final double TOLERANCE_M = 10d;
    public static final int MAX_POINTS = 500;

    private static final double METRES_PER_DEGREE = 6_371_000d * Math.PI / 180d;

    private RouteSimplifier() {
    }

    public static List<double[]> simplify(List<double[]> line) {
        return simplify(line, TOLERANCE_M, MAX_POINTS);
    }

    /** Doubles the tolerance until the line fits in {@code maxPoints} (at least 2). */
    public static List<double[]> simplify(List<double[]> line, double toleranceM, int maxPoints) {
        if (line.size() <= 2) {
            return new ArrayList<>(line);
        }
        int limit = Math.max(2, maxPoints);
        double tolerance = toleranceM;
        List<double[]> result = douglasPeucker(line, tolerance);
        while (result.size() > limit) {
            tolerance *= 2;
            result = douglasPeucker(line, tolerance);
        }
        return result;
    }

    private static List<double[]> douglasPeucker(List<double[]> line, double toleranceM) {
        int last = line.size() - 1;
        boolean[] keep = new boolean[line.size()];
        keep[0] = true;
        keep[last] = true;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[] { 0, last });
        while (!stack.isEmpty()) {
            int[] span = stack.pop();
            int from = span[0];
            int to = span[1];
            if (to - from < 2) {
                continue;
            }
            double furthest = -1d;
            int index = -1;
            for (int i = from + 1; i < to; i++) {
                double distance = metresFromSegment(line.get(i), line.get(from), line.get(to));
                if (distance > furthest) {
                    furthest = distance;
                    index = i;
                }
            }
            if (furthest > toleranceM) {
                keep[index] = true;
                stack.push(new int[] { from, index });
                stack.push(new int[] { index, to });
            }
        }
        List<double[]> result = new ArrayList<>();
        for (int i = 0; i < keep.length; i++) {
            if (keep[i]) {
                result.add(line.get(i));
            }
        }
        return result;
    }

    private static double metresFromSegment(double[] point, double[] start, double[] end) {
        double cosLat = Math.cos(Math.toRadians(start[0]));
        double px = (point[1] - start[1]) * cosLat * METRES_PER_DEGREE;
        double py = (point[0] - start[0]) * METRES_PER_DEGREE;
        double ex = (end[1] - start[1]) * cosLat * METRES_PER_DEGREE;
        double ey = (end[0] - start[0]) * METRES_PER_DEGREE;
        double lengthSquared = ex * ex + ey * ey;
        double t = lengthSquared == 0d ? 0d : Math.max(0d, Math.min(1d, (px * ex + py * ey) / lengthSquared));
        double dx = px - t * ex;
        double dy = py - t * ey;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
