package com.acetelecom.repairdesk.feature.location.service;

import java.net.URI;
import java.util.Optional;

import com.acetelecom.repairdesk.feature.location.service.CustomerPinParser.Pin;

/**
 * Opens a short Maps link and stops at the first hop that contains a pin.
 * Only Google Maps hosts are followed.
 */
class PinLinkFollower {

    private static final int MAX_HOPS = 4;
    private static final int MAX_BODY = 80_000;

    private final PinPageClient client;

    PinLinkFollower(PinPageClient client) {
        this.client = client;
    }

    Optional<Pin> follow(URI start) {
        URI current = start;
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            if (!CustomerPinParser.allowedMapsHost(current)) {
                return Optional.empty();
            }
            PinPageClient.Page page = client.fetch(current);
            if (page == null) {
                return Optional.empty();
            }
            if (page.status() >= 300 && page.status() < 400 && page.location() != null) {
                URI next = current.resolve(page.location());
                if (!CustomerPinParser.allowedMapsHost(next)) {
                    return Optional.empty();
                }
                Optional<Pin> pin = CustomerPinParser.pinIn(next.toString());
                if (pin.isPresent()) {
                    return pin;
                }
                current = next;
                continue;
            }
            if (page.status() >= 200 && page.status() < 300 && page.body() != null) {
                String body = page.body().length() > MAX_BODY ? page.body().substring(0, MAX_BODY) : page.body();
                return CustomerPinParser.pinIn(body);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }
}
