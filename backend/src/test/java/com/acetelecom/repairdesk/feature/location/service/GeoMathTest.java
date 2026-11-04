package com.acetelecom.repairdesk.feature.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoMathTest {

    @Test
    void andheriToBandraIsAboutSevenKilometres() {
        double metres = GeoMath.metresBetween(19.1197, 72.8468, 19.0544, 72.8402);
        assertThat(metres).isCloseTo(7_300, within(400d));
    }

    @Test
    void samePointIsInsideTheShopRadius() {
        assertThat(GeoMath.withinMetres(19.1197, 72.8468, 19.1197, 72.8468, 150)).isTrue();
    }
}
