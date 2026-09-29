package com.payflow.reconciliation.application.port.out;

import com.payflow.reconciliation.application.port.in.ReconciliationUseCase.MismatchView;
import com.payflow.reconciliation.application.port.in.ReconciliationUseCase.OpenSummary;
import com.payflow.reconciliation.domain.Finding;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reconciliation's own records: runs and mismatches. A mismatch is keyed by (check, subject); repeated observations
 * update it (seen count, last seen), and one no longer observed is marked RESOLVED. Financial tables are never touched.
 */
public interface MismatchStorePort {

    void recordRun(UUID runId, Instant startedAt, Instant finishedAt, long recordsChecked, List<Finding> findings);

    PageResult<MismatchView> open(PageQuery page);

    List<OpenSummary> openSummary();
}
