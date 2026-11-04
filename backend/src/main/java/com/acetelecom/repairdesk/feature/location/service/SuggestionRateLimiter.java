package com.acetelecom.repairdesk.feature.location.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Caps place-suggestion calls at 60 an hour for one person. */
@Component
public class SuggestionRateLimiter {

    static final int LIMIT = 60;

    private final Map<UUID, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public boolean allow(UUID userId, Instant now) {
        return allow(userId, now, LIMIT);
    }

    boolean allow(UUID userId, Instant now, int limit) {
        Deque<Instant> recent = hits.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (recent) {
            Instant cutoff = now.minus(Duration.ofHours(1));
            while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
                recent.removeFirst();
            }
            if (recent.size() >= limit) {
                return false;
            }
            recent.addLast(now);
            return true;
        }
    }
}
