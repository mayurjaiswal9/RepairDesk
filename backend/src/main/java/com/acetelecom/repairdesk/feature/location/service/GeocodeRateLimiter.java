package com.acetelecom.repairdesk.feature.location.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** In-memory cap of 30 address lookups per user per hour. One app replica is enough for dev. */
@Component
public class GeocodeRateLimiter {

    static final int LIMIT = 30;

    private final Map<UUID, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public boolean allow(UUID userId, Instant now) {
        Deque<Instant> recent = hits.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (recent) {
            Instant cutoff = now.minus(Duration.ofHours(1));
            while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
                recent.removeFirst();
            }
            if (recent.size() >= LIMIT) {
                return false;
            }
            recent.addLast(now);
            return true;
        }
    }
}
