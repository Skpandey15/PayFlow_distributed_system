package com.payflow.platform.messaging.consumer;

/**
 * Hook points around event processing. None are registered in production. The failure-engineering tests
 * register one to inject crashes at exactly the two points that matter for at-least-once correctness:
 * <ul>
 *   <li>{@link #beforeCommit}: inside the business transaction, after the change and before COMMIT.</li>
 *   <li>{@link #afterCommit}: after COMMIT, before the listener returns (so before the offset commit).</li>
 * </ul>
 */
public interface EventProcessingInterceptor {

    default void beforeCommit(IncomingEvent event) {
    }

    default void afterCommit(IncomingEvent event) {
    }
}
