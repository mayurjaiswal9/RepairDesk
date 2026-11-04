package com.acetelecom.repairdesk.feature.location.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.location.service.CustomerPinParser.Pin;

@Service
public class CustomerPinService {

    private final PinLinkFollower follower;
    private final CustomerPinRateLimiter rateLimiter;

    public CustomerPinService(HttpPinPageClient pages, CustomerPinRateLimiter rateLimiter) {
        this.follower = new PinLinkFollower(pages);
        this.rateLimiter = rateLimiter;
    }

    public Optional<Pin> resolve(String text, UUID userId, Instant now) {
        Optional<Pin> direct = CustomerPinParser.pinIn(text);
        if (direct.isPresent()) {
            return direct;
        }
        return CustomerPinParser.shortLink(text).map(link -> {
            if (!rateLimiter.allow(userId, now)) {
                throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                        "Too many location links. Try again in an hour, or drop the pin on the map.");
            }
            return follower.follow(link);
        }).flatMap(found -> found);
    }
}
