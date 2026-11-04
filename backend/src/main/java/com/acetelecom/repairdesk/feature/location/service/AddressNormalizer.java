package com.acetelecom.repairdesk.feature.location.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class AddressNormalizer {

    private AddressNormalizer() {
    }

    /** Lower-case, collapsed spaces, and a single trailing "india" so cache keys match. */
    public static String normalize(String address) {
        String collapsed = address.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        if (collapsed.endsWith("india")) {
            return collapsed;
        }
        return collapsed + ", india";
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
