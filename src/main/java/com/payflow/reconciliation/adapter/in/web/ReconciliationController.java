package com.payflow.reconciliation.adapter.in.web;

import com.payflow.platform.web.PageResponse;
import com.payflow.reconciliation.application.port.in.ReconciliationUseCase;
import com.payflow.reconciliation.application.port.in.ReconciliationUseCase.RunReport;
import com.payflow.reconciliation.application.port.in.ReconciliationUseCase.MismatchView;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read findings and trigger a run (scope ops:reconciliation, rate limited per operator). No correction endpoint. */
@RestController
@Validated
@Tag(name = "Operations: reconciliation")
@RequestMapping(path = "/api/v1/ops/reconciliation", produces = MediaType.APPLICATION_JSON_VALUE)
class ReconciliationController {

    private final ReconciliationUseCase reconciliation;

    ReconciliationController(ReconciliationUseCase reconciliation) {
        this.reconciliation = reconciliation;
    }

    @GetMapping("/mismatches")
    @Operation(summary = "Open mismatches, most severe and oldest first")
    PageResponse<MismatchView> mismatches(Actor actor, @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "50") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
        return PageResponse.from(reconciliation.openMismatches(actor, new PageQuery(page, size)), m -> m);
    }

    @PostMapping("/runs")
    @Operation(summary = "Run reconciliation now (skipped if another instance is running one)")
    RunReport run(Actor actor) {
        return reconciliation.run(actor);
    }
}
