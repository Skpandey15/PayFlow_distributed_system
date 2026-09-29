package com.payflow.platform.web.traffic;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrafficControlProperties.class)
class TrafficControlConfiguration implements WebMvcConfigurer {

    private final TrafficControlInterceptor interceptor;

    TrafficControlConfiguration(TrafficControlInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/payments", "/api/v1/ops/**");
    }
}
