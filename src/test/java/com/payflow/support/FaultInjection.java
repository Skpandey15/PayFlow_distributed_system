package com.payflow.support;

import com.payflow.platform.messaging.consumer.EventProcessingInterceptor;
import com.payflow.platform.messaging.consumer.IncomingEvent;
import com.payflow.shared.application.DependencyUnavailableException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic crash injection at the two points that matter for at-least-once processing. It is inert unless a
 * test arms it for a specific (consumer group, eventId), so it can live in the shared test context. Keying by
 * consumer matters: several groups consume the same event.
 */
public class FaultInjection implements EventProcessingInterceptor {

    public enum Point { BEFORE_COMMIT, AFTER_COMMIT }

    private record Target(String consumer, UUID eventId) {
    }

    private record Fault(Point point, AtomicInteger remaining) {
    }

    private final Map<Target, Fault> faults = new ConcurrentHashMap<>();

    /** Fail {@code consumer}'s processing of {@code eventId} at {@code point}, {@code times} times. */
    public void arm(String consumer, UUID eventId, Point point, int times) {
        faults.put(new Target(consumer, eventId), new Fault(point, new AtomicInteger(times)));
    }

    public void disarm(String consumer, UUID eventId) {
        faults.remove(new Target(consumer, eventId));
    }

    @Override
    public void beforeCommit(IncomingEvent event) {
        maybeFail(event, Point.BEFORE_COMMIT);
    }

    @Override
    public void afterCommit(IncomingEvent event) {
        maybeFail(event, Point.AFTER_COMMIT);
    }

    private void maybeFail(IncomingEvent event, Point point) {
        Fault fault = faults.get(new Target(event.consumer(), event.eventId()));
        if (fault != null && fault.point() == point && fault.remaining().getAndDecrement() > 0) {
            throw new DependencyUnavailableException("INJECTED_CRASH_" + point,
                    "Injected crash " + point + " for " + event.eventId(), null);
        }
    }
}
