package com.acetelecom.repairdesk.feature.location.dto;

public record GeocodeResponse(double latitude, double longitude, String formattedAddress) {
}
