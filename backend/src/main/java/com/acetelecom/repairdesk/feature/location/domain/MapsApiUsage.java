package com.acetelecom.repairdesk.feature.location.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maps_api_usage")
@IdClass(MapsApiUsage.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class MapsApiUsage {

    public static final String GEOCODE = "GEOCODE";
    public static final String ROUTES = "ROUTES";
    public static final String SUGGESTIONS = "SUGGESTIONS";
    public static final String PLACE_DETAILS = "PLACE_DETAILS";

    @Id
    @Column(name = "usage_day", nullable = false)
    private LocalDate usageDay;

    @Id
    @Column(nullable = false, length = 20)
    private String api;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    /** Null on rows written before a company was recorded. Existing counts are not backfilled. */
    @Column(name = "company_id")
    private UUID companyId;

    public MapsApiUsage(LocalDate usageDay, String api) {
        this.usageDay = usageDay;
        this.api = api;
    }

    public static class Key implements Serializable {
        private LocalDate usageDay;
        private String api;

        public Key() {
        }

        public Key(LocalDate usageDay, String api) {
            this.usageDay = usageDay;
            this.api = api;
        }

        public LocalDate getUsageDay() {
            return usageDay;
        }

        public String getApi() {
            return api;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(usageDay, key.usageDay) && Objects.equals(api, key.api);
        }

        @Override
        public int hashCode() {
            return Objects.hash(usageDay, api);
        }
    }
}
