package com.acetelecom.repairdesk.common.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight, public liveness endpoint (separate from Actuator health) that
 * confirms the API is reachable. Full path: {@code /api/v1/ping}.
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "service", "repairdesk",
                "timestamp", Instant.now().toString());
    }
}
