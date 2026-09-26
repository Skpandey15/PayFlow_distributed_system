package com.payflow.platform;

import com.payflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full context against real PostgreSQL + MongoDB: Flyway migrations apply, Hibernate
 * {@code ddl-auto=validate} confirms every JPA entity matches the migrated schema, and probes and docs are served.
 */
@IntegrationTest
class ApplicationSmokeIT {

    @Autowired
    MockMvc mvc;

    @Test
    void livenessAndReadinessProbesArePublicAndUp() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthDoesNotLeakComponentDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void everyResponseCarriesACorrelationId() throws Exception {
        mvc.perform(get("/actuator/health/liveness").header("X-Correlation-Id", "abc-123"))
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
        // A malicious value (log injection attempt) is replaced, never echoed.
        mvc.perform(get("/actuator/health/liveness").header("X-Correlation-Id", "bad\r\nvalue"))
                .andExpect(header().string("X-Correlation-Id", org.hamcrest.Matchers.not(containsString("bad"))));
    }

    @Test
    void openApiDocumentDescribesTheVersionedPaymentApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/payments")))
                .andExpect(content().string(containsString("Idempotency-Key")))
                .andExpect(content().string(containsString("bearer-jwt")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("\"actor\""))));
    }

    @Test
    void nonExposedActuatorEndpointsAreNotReachable() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
