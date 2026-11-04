package com.acetelecom.repairdesk.feature.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PinLinkFollowerTest {

    @Test
    void stopsWhenTheRedirectCarriesThePin() {
        List<URI> fetched = new ArrayList<>();
        PinLinkFollower follower = new PinLinkFollower(uri -> {
            fetched.add(uri);
            return new PinPageClient.Page(302, "https://www.google.com/maps/@17.50,78.40,17z", "");
        });

        CustomerPinParser.Pin pin = follower.follow(URI.create("https://maps.app.goo.gl/abc")).orElseThrow();

        assertThat(pin.latitude()).isEqualTo(17.50);
        assertThat(pin.longitude()).isEqualTo(78.40);
        assertThat(fetched).containsExactly(URI.create("https://maps.app.goo.gl/abc"));
    }

    @Test
    void refusesARedirectAwayFromGoogle() {
        PinLinkFollower follower = new PinLinkFollower(uri ->
                new PinPageClient.Page(302, "https://example.com/@17.50,78.40", ""));
        assertThat(follower.follow(URI.create("https://goo.gl/maps/abc"))).isEmpty();
    }
}
