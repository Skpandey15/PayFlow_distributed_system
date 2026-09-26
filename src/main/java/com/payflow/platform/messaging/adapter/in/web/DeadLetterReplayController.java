package com.payflow.platform.messaging.adapter.in.web;

import com.payflow.platform.messaging.dlq.DeadLetterReplayService;
import com.payflow.platform.messaging.dlq.DeadLetterReplayService.ReplayResult;
import com.payflow.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/** Operator endpoint for controlled dead-letter replay (scope {@code ops:dlq-replay}; audited in logs). */
@RestController
@RequestMapping(path = "/api/v1/ops/dead-letters", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Operations")
class DeadLetterReplayController {

    static final String PERMISSION = "ops:dlq-replay";

    private final DeadLetterReplayService replay;

    DeadLetterReplayController(DeadLetterReplayService replay) {
        this.replay = replay;
    }

    record ReplayRequest(@NotBlank String dltTopic, @NotNull @Min(0) Integer partition, @NotNull @Min(0) Long offset) {
    }

    @PostMapping(path = "/replay", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replay one dead-lettered record to its consumer group's retry topic")
    ResponseEntity<ReplayResult> replay(Actor actor, @Valid @RequestBody ReplayRequest request) {
        requirePermission(actor, PERMISSION);
        return ResponseEntity.accepted()
                .body(replay.replay(request.dltTopic(), request.partition(), request.offset(), actor.subject()));
    }
}
