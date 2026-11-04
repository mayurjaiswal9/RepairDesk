package com.acetelecom.repairdesk.feature.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

class CustomerPinParserTest {

    @Test
    void readsAQueryPinFromAWhatsAppMessage() {
        String message = "Customer location https://maps.google.com/?q=17.412345,78.448901 please come";
        CustomerPinParser.Pin pin = CustomerPinParser.pinIn(message).orElseThrow();
        assertThat(pin.latitude()).isEqualTo(17.412345);
        assertThat(pin.longitude()).isEqualTo(78.448901);
        assertThat(CustomerPinParser.shortLink(message)).isEmpty();
    }

    @Test
    void readsAnAtPinAndAGeoUri() {
        assertThat(CustomerPinParser.pinIn("https://www.google.com/maps/@17.43,78.45,17z").orElseThrow().latitude())
                .isEqualTo(17.43);
        assertThat(CustomerPinParser.pinIn("geo:19.141212,72.830906").orElseThrow().longitude())
                .isEqualTo(72.830906);
        assertThat(CustomerPinParser.pinIn("https://www.google.com/maps/place/Shop/data=!3d17.385044!4d78.486671")
                .orElseThrow().latitude()).isEqualTo(17.385044);
    }

    @Test
    void ignoresAPhotoCaptionAndAPlaceNameWithoutAPin() {
        assertThat(CustomerPinParser.pinIn("See the photo of the shop")).isEmpty();
        assertThat(CustomerPinParser.pinIn("https://www.google.com/maps/place/Khairatabad")).isEmpty();
    }

    @Test
    void findsAShortLinkOnlyWhenTheMessageHasNoPin() {
        URI link = CustomerPinParser.shortLink("I am here https://maps.app.goo.gl/abcDEF123").orElseThrow();
        assertThat(link.getHost()).isEqualTo("maps.app.goo.gl");
        assertThat(CustomerPinParser.shortLink("https://maps.app.goo.gl/abc?q=17.10,78.20")).isEmpty();
        assertThat(CustomerPinParser.allowedMapsHost(URI.create("https://example.com/maps"))).isFalse();
    }
}
