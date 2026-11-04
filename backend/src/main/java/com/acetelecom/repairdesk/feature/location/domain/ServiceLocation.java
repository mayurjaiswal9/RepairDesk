package com.acetelecom.repairdesk.feature.location.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Customer service address shared by repair requests and jobs.
 * Column names are fixed so both tables use the same shape.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class ServiceLocation {

    @Column(name = "service_address_line", length = 300)
    private String addressLine;

    @Column(name = "service_landmark", length = 150)
    private String landmark;

    @Column(name = "service_city", length = 100)
    private String city;

    @Column(name = "service_pincode", length = 6)
    private String pincode;

    @Column(name = "service_lat")
    private Double latitude;

    @Column(name = "service_lng")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_pin_source", length = 20)
    private PinSource pinSource;

    public boolean isBlank() {
        return addressLine == null && landmark == null && city == null && pincode == null
                && latitude == null && longitude == null;
    }

    public static ServiceLocation copyOf(ServiceLocation src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        ServiceLocation copy = new ServiceLocation();
        copy.addressLine = src.addressLine;
        copy.landmark = src.landmark;
        copy.city = src.city;
        copy.pincode = src.pincode;
        copy.latitude = src.latitude;
        copy.longitude = src.longitude;
        copy.pinSource = src.pinSource;
        return copy;
    }
}
