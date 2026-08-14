package com.acetelecom.repairdesk.feature.devicecategory.domain;

/**
 * Controls how the customer's "brand" field behaves on the New Request
 * screen for a given device category (PROD-004).
 */
public enum BrandMode {

    /** Admin curates a list of brands for this category; customer picks
     * from a searchable list. "Others" is always appended automatically. */
    CONFIGURED,

    /** No curated list: customer chooses between "Others" (quick, no typing)
     * or "Custom" free-text entry. */
    FREE_TEXT
}
