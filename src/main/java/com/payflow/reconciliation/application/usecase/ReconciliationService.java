package com.payflow.reconciliation.application.usecase;

import com.payflow.reconciliation.application.port.in.ReconciliationUseCase;
import com.payflow.reconciliation.application.port.out.MismatchStorePort;
import com.payflow.reconciliation.application.port.in.ReconciliationUseCase.MismatchView;
import com.payflow.reconciliation.application.port.in.ReconciliationUseCase.OpenSummary;
import com.payflow.reconciliation.application.port.out.ReconciliationSourcePort;
import com.payflow.reconciliation.application.port.out.ReconciliationSourcePort.Snapshot;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;
import com.payflow.shared.domain.Identifiers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/**
 * detect → classify → record → (metrics/alerts, by the infrastructure job) → investigate → controlled correction.
 * This service stops after "record": it has no write access to any financial table, so a reconciliation bug cannot
 * corrupt money, and no mismatch is ever "fixed" by overwriting one side with the other.
 */
public class ReconciliationService implements ReconciliationUseCase {

    public static final String PERMISSION = "ops:reconciliation";

    private final ReconciliationSourcePort source;
    private final MismatchStorePort store;
    private final Clock clock;
    private final Duration grace;

    /**
     * @param grace records changed more recently than this are not compared by eventually consistent checks (an event
     *              still in flight is not drift). Must exceed normal end-to-end propagation (seconds) with margin.
     */
    public ReconciliationService(ReconciliationSourcePort source, MismatchStorePort store, Clock clock, Duration grace) {
        this.source = source;
        this.store = store;
        this.clock = clock;
        this.grace = grace;
    }

    @Override
    public RunReport run() {
        Instant started = clock.instant();
        Optional<Snapshot> snapshot = source.evaluate(started.minus(grace));
        if (snapshot.isEmpty()) {
            return new RunReport(null, true, Duration.ZERO, 0, Map.of());
        }
        UUID runId = Identifiers.timeOrderedUuid();
        long checked = snapshot.get().recordsChecked().values().stream().mapToLong(Long::longValue).sum();
        Instant finished = clock.instant();
        store.recordRun(runId, started, finished, checked, snapshot.get().findings());
        Map<String, Long> byCheck = snapshot.get().findings().stream()
                .collect(Collectors.groupingBy(f -> f.check().name(), TreeMap::new, Collectors.counting()));
        return new RunReport(runId, false, Duration.between(started, finished), checked, byCheck);
    }

    @Override
    public RunReport run(Actor actor) {
        requirePermission(actor, PERMISSION);
        return run();
    }

    @Override
    public PageResult<MismatchView> openMismatches(Actor actor, PageQuery page) {
        requirePermission(actor, PERMISSION);
        return store.open(page);
    }

    @Override
    public List<OpenSummary> openSummary() {
        return store.openSummary();
    }
}
