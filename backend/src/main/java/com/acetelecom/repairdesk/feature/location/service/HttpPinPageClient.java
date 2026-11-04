package com.acetelecom.repairdesk.feature.location.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

/** Fetches one Maps page and does not follow redirects itself. */
@Component
class HttpPinPageClient implements PinPageClient {

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    @Override
    public Page fetch(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", "RepairDesk")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String location = response.headers().firstValue("location").orElse(null);
            return new Page(response.statusCode(), location, response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Page(0, null, null);
        }
    }
}
