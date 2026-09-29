package com.payflow.settlement.application.port.in;

import com.payflow.settlement.domain.SettlementRail;

/**
 * Re-submits settlements parked while their rail's circuit was open (see {@link SubmitSettlementUseCase}). Run
 * periodically per rail. It stops at the first instruction that is parked again: the circuit is still open, and
 * the rest would fail fast the same way.
 */
public interface ResumeParkedSettlementsUseCase {

    ResumeReport resumeParked(SettlementRail rail, int limit);

    /**
     * @param claimed     parked settlements this replica took (others are skipped by concurrent replicas)
     * @param answered    re-submissions the rail answered (completed or declined)
     * @param stillParked true if the circuit refused again, so the remaining claimed ones were left for the next run
     */
    record ResumeReport(int claimed, int answered, boolean stillParked) {
        public static final ResumeReport NOTHING = new ResumeReport(0, 0, false);
    }
}
