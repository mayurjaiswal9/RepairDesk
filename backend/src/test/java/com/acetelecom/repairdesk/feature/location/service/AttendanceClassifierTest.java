package com.acetelecom.repairdesk.feature.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.acetelecom.repairdesk.feature.location.domain.LocationFlag;
import com.acetelecom.repairdesk.feature.location.service.AttendanceClassifier.Fix;
import com.acetelecom.repairdesk.feature.location.service.AttendanceClassifier.Outcome;

class AttendanceClassifierTest {

    private static final double SHOP_LAT = 19.1412127;
    private static final double SHOP_LNG = 72.8309064;
    private static final int RADIUS = 150;

    @Test
    void missingFixStarts() {
        Outcome outcome = AttendanceClassifier.classify(null, SHOP_LAT, SHOP_LNG, RADIUS, false);
        assertThat(outcome.flag()).isEqualTo(LocationFlag.MISSING);
        assertThat(outcome.allowed()).isTrue();
    }

    @Test
    void wideAccuracyIsUncertainAndStarts() {
        Fix fix = new Fix(SHOP_LAT, SHOP_LNG, 400d, false);
        Outcome outcome = AttendanceClassifier.classify(fix, SHOP_LAT, SHOP_LNG, RADIUS, false);
        assertThat(outcome.flag()).isEqualTo(LocationFlag.UNCERTAIN);
        assertThat(outcome.allowed()).isTrue();
    }

    @Test
    void mockedFixStartsEvenInsideTheShop() {
        Fix fix = new Fix(SHOP_LAT, SHOP_LNG, 8d, true);
        Outcome outcome = AttendanceClassifier.classify(fix, SHOP_LAT, SHOP_LNG, RADIUS, false);
        assertThat(outcome.flag()).isEqualTo(LocationFlag.MOCKED);
        assertThat(outcome.allowed()).isTrue();
    }

    @Test
    void trustworthyFixInsideTheShopStarts() {
        Fix fix = new Fix(SHOP_LAT, SHOP_LNG, 20d, false);
        Outcome outcome = AttendanceClassifier.classify(fix, SHOP_LAT, SHOP_LNG, RADIUS, false);
        assertThat(outcome.flag()).isEqualTo(LocationFlag.INSIDE);
        assertThat(outcome.allowed()).isTrue();
    }

    @Test
    void accuracyEqualToTheRadiusIsStillTrustworthy() {
        Fix fix = new Fix(SHOP_LAT, SHOP_LNG, 150d, false);
        Outcome outcome = AttendanceClassifier.classify(fix, SHOP_LAT, SHOP_LNG, RADIUS, false);
        assertThat(outcome.flag()).isEqualTo(LocationFlag.INSIDE);
        assertThat(outcome.allowed()).isTrue();
    }

    @Test
    void trustworthyFixOutsideIsRefusedUntilOutsideStartsAreAllowed() {
        Fix fix = new Fix(SHOP_LAT + 0.004, SHOP_LNG, 15d, false);
        Outcome refused = AttendanceClassifier.classify(fix, SHOP_LAT, SHOP_LNG, RADIUS, false);
        assertThat(refused.flag()).isEqualTo(LocationFlag.OUTSIDE);
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.distanceMetres()).isGreaterThan(RADIUS);

        Outcome allowed = AttendanceClassifier.classify(fix, SHOP_LAT, SHOP_LNG, RADIUS, true);
        assertThat(allowed.flag()).isEqualTo(LocationFlag.OUTSIDE);
        assertThat(allowed.allowed()).isTrue();
    }

    @Test
    void trustworthyFixWithNoShopPinIsUncertainAndStarts() {
        Fix fix = new Fix(SHOP_LAT, SHOP_LNG, 10d, false);
        Outcome outcome = AttendanceClassifier.classify(fix, null, null, RADIUS, false);
        assertThat(outcome.flag()).isEqualTo(LocationFlag.UNCERTAIN);
        assertThat(outcome.allowed()).isTrue();
        assertThat(outcome.distanceMetres()).isNull();
    }

    @Test
    void halfACoordinateIsTreatedAsMissing() {
        Fix fix = new Fix(SHOP_LAT, null, 10d, false);
        Outcome outcome = AttendanceClassifier.classify(fix, SHOP_LAT, SHOP_LNG, RADIUS, false);
        assertThat(outcome.flag()).isEqualTo(LocationFlag.MISSING);
        assertThat(outcome.allowed()).isTrue();
    }
}
