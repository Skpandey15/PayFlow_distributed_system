package com.payflow.reconciliation.application.port.out;

import com.payflow.reconciliation.domain.Finding;
import com.payflow.reconciliation.domain.ReconciliationCheck;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Evaluates every invariant against one consistent snapshot of the authoritative stores. */
public interface ReconciliationSourcePort {

    /**
     * @param settledBefore eventually consistent checks ignore records changed after this instant
     * @return empty when another instance is running a reconciliation right now (single runner across replicas)
     */
    Optional<Snapshot> evaluate(Instant settledBefore);

    record Snapshot(Map<ReconciliationCheck, Long> recordsChecked, List<Finding> findings) {
    }
}
