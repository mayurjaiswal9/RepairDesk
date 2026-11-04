package com.acetelecom.repairdesk.feature.location.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Caps short-link opens at 30 an hour for one person. A link that already has a pin does not count. */
@Component
public class CustomerPinRateLimiter {

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
