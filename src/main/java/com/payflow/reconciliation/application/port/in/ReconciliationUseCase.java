package com.payflow.reconciliation.application.port.in;

import com.payflow.shared.application.Actor;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Continuous reconciliation (WP-03, K4): detect, classify, expose. Correction is a separate, human-controlled process. */
public interface ReconciliationUseCase {

    /** Scheduled/system run. Returns {@code skipped=true} when another replica is running one. */
    RunReport run();

    /** Operator-triggered run (requires ops:reconciliation). */
    RunReport run(Actor actor);

    PageResult<MismatchView> openMismatches(Actor actor, PageQuery page);

    /** Aggregates for metrics and alerting (no identifiers). */
    List<OpenSummary> openSummary();

    record RunReport(UUID runId, boolean skipped, Duration duration, long recordsChecked, Map<String, Long> findingsByCheck) {
    }

    record MismatchView(UUID id, String check, String severity, String subjectType, String subjectId, String currency,
                        BigDecimal expected, BigDecimal actual, int timesSeen, Instant firstSeenAt, Instant lastSeenAt) {
    }

    /** Aggregates for metrics: count per check (and whether confirmed = seen in at least two runs). */
    record OpenSummary(String check, String severity, boolean confirmed, long count, Instant oldestFirstSeenAt,
                       String currency, BigDecimal absoluteDelta) {
    }
}
