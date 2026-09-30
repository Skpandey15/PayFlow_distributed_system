package com.payflow.platform.web.traffic;

import com.payflow.platform.messaging.KafkaLagMonitor;
import com.payflow.platform.messaging.outbox.OutboxRelayScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The in-flight bulkhead on payment acceptance: bounded, immediate rejection, permits always returned. */
class TrafficControlInterceptorTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final TrafficControlInterceptor interceptor = new TrafficControlInterceptor(
            new TrafficControlProperties(1000, 10, Duration.ofSeconds(60), Duration.ofSeconds(30), 2, 0, 0),
            new OutboxRelayScheduler(List.of(), null, Clock.systemUTC()), new StaticListableBeanFactory().getBeanProvider(KafkaLagMonitor.class),
            new StaticListableBeanFactory().getBeanProvider(InFlightWork.class),
            meters, JsonMapper.builder().build(),
            Clock.systemUTC());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest createPayment() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments");
        request.setRequestURI("/api/v1/payments");
        return request;
    }

    @Test
    void acceptanceBeyondTheInFlightLimitIsShedImmediatelyAndPermitsAreReturned() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice", null, List.of()));
        MockHttpServletRequest first = createPayment();
        MockHttpServletRequest second = createPayment();
        assertThat(interceptor.preHandle(first, new MockHttpServletResponse(), null)).isTrue();
        assertThat(interceptor.preHandle(second, new MockHttpServletResponse(), null)).isTrue();

        MockHttpServletResponse shed = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(createPayment(), shed, null)).isFalse();
        assertThat(shed.getStatus()).isEqualTo(503);
        assertThat(shed.getHeader("Retry-After")).isEqualTo("1");
        assertThat(shed.getContentAsString()).contains("PAYMENTS_OVERLOADED");
        assertThat(meters.get("payflow.traffic.payments.in_flight").gauge().value()).isEqualTo(2.0);

        interceptor.afterCompletion(first, new MockHttpServletResponse(), null, null);
        assertThat(interceptor.preHandle(createPayment(), new MockHttpServletResponse(), null))
                .as("a completed request returns its slot").isTrue();
        assertThat(meters.get("payflow.traffic.rejected").tag("policy", "concurrency").counter().count()).isEqualTo(1);
    }

    @Test
    void workInProgressWindowAdmitsHalfTheFreeRoomThenSheds() throws Exception {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("work", (InFlightWork) () -> 90); // limit 100: room 10, window 5
        TrafficControlInterceptor windowed = new TrafficControlInterceptor(
                new TrafficControlProperties(1000, 10, Duration.ofSeconds(60), Duration.ofSeconds(30), 0, 0, 100),
                new OutboxRelayScheduler(List.of(), null, Clock.systemUTC()),
                new StaticListableBeanFactory().getBeanProvider(KafkaLagMonitor.class), beans.getBeanProvider(InFlightWork.class),
                meters, JsonMapper.builder().build(), Clock.systemUTC());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice", null, List.of()));
        for (int i = 0; i < 5; i++) {
            assertThat(windowed.preHandle(createPayment(), new MockHttpServletResponse(), null)).isTrue();
        }
        MockHttpServletResponse shed = new MockHttpServletResponse();
        assertThat(windowed.preHandle(createPayment(), shed, null)).isFalse();
        assertThat(shed.getStatus()).isEqualTo(503);
        assertThat(shed.getContentAsString()).contains("PAYMENTS_BUSY");
        assertThat(meters.get("payflow.traffic.rejected").tag("policy", "work-in-progress").counter().count()).isEqualTo(1);
    }

    @Test
    void readsAreNeverLimited() throws Exception {
        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/api/v1/payments/x");
        for (int i = 0; i < 10; i++) {
            assertThat(interceptor.preHandle(read, new MockHttpServletResponse(), null)).isTrue();
        }
    }
}
