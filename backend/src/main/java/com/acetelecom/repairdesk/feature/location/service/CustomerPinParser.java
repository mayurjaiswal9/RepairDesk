package com.acetelecom.repairdesk.feature.location.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a pin out of a message the customer sent. A photo is not a pin, so only
 * a Maps link or a {@code geo:} URI is accepted.
 */
public final class CustomerPinParser {

    private static final Pattern URL = Pattern.compile("https://[^\\s<>'\"]+");
    private static final Pattern GEO = Pattern.compile("geo:(-?\\d{1,2}\\.\\d{2,}),\\s*(-?\\d{1,3}\\.\\d{2,})");
    private static final Pattern QUERY = Pattern.compile(
            "[?&](?:q|query|ll|center)=(-?\\d{1,2}\\.\\d{2,}),\\s*(-?\\d{1,3}\\.\\d{2,})");
    private static final Pattern AT = Pattern.compile("@(-?\\d{1,2}\\.\\d{2,}),\\s*(-?\\d{1,3}\\.\\d{2,})");
    private static final Pattern BANG = Pattern.compile("!3d(-?\\d{1,2}\\.\\d{2,})!4d(-?\\d{1,3}\\.\\d{2,})");

    private CustomerPinParser() {
    }

    public record Pin(double latitude, double longitude) {
    }

    public static Optional<Pin> pinIn(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher geo = GEO.matcher(text);
        if (geo.find()) {
            Optional<Pin> pin = pin(geo.group(1), geo.group(2));
            if (pin.isPresent()) {
                return pin;
            }
        }
        for (String url : urls(text)) {
            Optional<Pin> pin = pinInUrl(url);
            if (pin.isPresent()) {
                return pin;
            }
        }
        return Optional.empty();
    }

    /** A short Maps link that still needs to be opened. Empty when a pin is already in the text. */
    public static Optional<URI> shortLink(String text) {
        if (text == null || pinIn(text).isPresent()) {
            return Optional.empty();
        }
        for (String url : urls(text)) {
            URI uri = tryUri(url);
            if (uri != null && isShortHost(uri)) {
                return Optional.of(uri);
            }
        }
        return Optional.empty();
    }

    public static boolean allowedMapsHost(URI uri) {
        if (uri == null || uri.getUserInfo() != null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        return host.equals("maps.app.goo.gl")
                || host.equals("goo.gl")
                || host.equals("maps.google.com")
                || host.equals("www.google.com")
                || host.equals("google.com")
                || host.equals("maps.google.co.in")
                || host.equals("www.google.co.in");
    }

    private static Optional<Pin> pinInUrl(String url) {
        for (Pattern pattern : List.of(QUERY, AT, BANG)) {
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                Optional<Pin> pin = pin(matcher.group(1), matcher.group(2));
                if (pin.isPresent()) {
                    return pin;
                }
            }
        }
        return Optional.empty();
    }

    private static List<String> urls(String text) {
        Matcher matcher = URL.matcher(text);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            found.add(trimTrail(matcher.group()));
        }
        return found;
    }

    private static String trimTrail(String url) {
        int end = url.length();
        while (end > 0 && ".,);]>".indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
    }

    private static boolean isShortHost(URI uri) {
        if (!allowedMapsHost(uri)) {
            return false;
        }
        String host = uri.getHost().toLowerCase();
        return host.equals("maps.app.goo.gl") || host.equals("goo.gl");
    }

    private static URI tryUri(String raw) {
        try {
            return URI.create(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Optional<Pin> pin(String latText, String lngText) {
        try {
            double lat = Double.parseDouble(latText);
            double lng = Double.parseDouble(lngText);
            if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                return Optional.empty();
            }
            return Optional.of(new Pin(lat, lng));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
