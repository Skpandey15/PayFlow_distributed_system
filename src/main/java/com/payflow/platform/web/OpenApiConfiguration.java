package com.payflow.platform.web;

import com.payflow.shared.application.Actor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    static {
        // Actor is resolved from the verified JWT, never from request input, so hide it from the contract.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Actor.class);
    }

    @Bean
    OpenAPI payflowOpenApi() {
        return new OpenAPI()
                .info(new Info().title("PayFlow API").version("v1")
                        .description("""
                                Payment processing API (WP-01 foundation). All endpoints require an OAuth2 bearer JWT \
                                issued by the PayFlow identity provider. Errors use RFC 9457 problem details. \
                                POST /api/v1/payments requires an Idempotency-Key header."""))
                .components(new Components().addSecuritySchemes("bearer-jwt", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
