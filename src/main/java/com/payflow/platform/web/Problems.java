package com.payflow.platform.web;

import com.payflow.platform.observability.CorrelationIdFilter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.Locale;

/** Factory for RFC 9457 problem details with PayFlow's stable {@code code} and {@code correlationId} extensions. */
public final class Problems {

    public static final String TYPE_BASE = "https://problems.payflow.example/";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(typeFor(code));
        problem.setProperty("code", code);
        enrich(problem);
        return problem;
    }

    /** Adds the correlation id so a client-visible error can be joined to server logs. */
    public static void enrich(ProblemDetail problem) {
        String correlationId = CorrelationIdFilter.current();
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }

    public static URI typeFor(String code) {
        return URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
