package com.payflow.platform.messaging.consumer;

/** What happened to a successfully handled event (all outcomes lead to the offset being committed). */
public enum ConsumerOutcome {
    /** Business effect applied. */
    PROCESSED,
    /** Same eventId already processed by this consumer (redelivery or replay): no effect. */
    DUPLICATE,
    /** Valid, but semantically superseded (for example a saga reply for a step the saga already left): no effect. */
    STALE,
    /** Valid, but not relevant to this consumer (a type it does not act on). */
    IGNORED
}
