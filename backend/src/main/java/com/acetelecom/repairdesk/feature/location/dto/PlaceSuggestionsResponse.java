package com.acetelecom.repairdesk.feature.location.dto;

import java.util.List;

public record PlaceSuggestionsResponse(List<PlaceSuggestionResponse> suggestions) {

    public record PlaceSuggestionResponse(String placeId, String primaryText, String secondaryText) {
    }
}
