package com.payflow.platform.web.traffic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A window: each sample grants half of the free room, so admissions can never outrun the room. */
class InFlightAdmissionTest {

    private final InFlightAdmission admission = new InFlightAdmission(500);

    @Test
    void grantsHalfOfTheFreeRoom() {
        assertThat(admission.credits(0)).isEqualTo(250);
        assertThat(admission.credits(400)).isEqualTo(50);
        assertThat(admission.credits(499)).isZero();
    }

    @Test
    void grantsNothingAtOrAboveTheLimit() {
        assertThat(admission.credits(500)).isZero();
        assertThat(admission.credits(5000)).isZero();
    }

    @Test
    void settlesBelowTheLimitWhateverTheArrivalRate() {
        // 1,000 arrivals per sample (far above the room), 20 completions per sample.
        long wip = 0;
        long maxWip = 0;
        for (int sample = 0; sample < 200; sample++) {
            wip += Math.min(1000, admission.credits(wip));
            wip = Math.max(0, wip - 20);
            maxWip = Math.max(maxWip, wip);
        }
        assertThat(maxWip).as("never above the limit").isLessThanOrEqualTo(500);
        assertThat(wip).as("settles near the limit").isBetween(440L, 500L);
    }

    @Test
    void zeroLimitMeansOff() {
        assertThat(new InFlightAdmission(0).enabled()).isFalse();
    }
}
