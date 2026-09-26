package com.payflow.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestMessagingConfiguration {

    @Bean
    FaultInjection faultInjection() {
        return new FaultInjection();
    }
}
