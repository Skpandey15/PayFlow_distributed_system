package com.payflow.platform.messaging.consumer;

@FunctionalInterface
public interface EventHandler {

    ConsumerOutcome handle(IncomingEvent event);
}
