package com.acetelecom.repairdesk.feature.location.service;

import java.net.URI;
import java.util.Optional;

import com.acetelecom.repairdesk.feature.location.service.CustomerPinParser.Pin;

/** One page fetch with redirects left to the caller. */
interface PinPageClient {

    record Page(int status, String location, String body) {
    }

    Page fetch(URI uri);
}
