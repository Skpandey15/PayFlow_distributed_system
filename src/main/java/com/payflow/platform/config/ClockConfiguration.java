package com.payflow.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    /**
     * Single injectable time source (tests substitute a fixed clock). UTC, and truncated to microseconds
     * because that is PostgreSQL's TIMESTAMPTZ precision. Without truncation an aggregate's timestamps
     * would differ before and after a database round-trip.
     */
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
