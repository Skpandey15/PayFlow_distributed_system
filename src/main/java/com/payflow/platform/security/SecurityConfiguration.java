package com.payflow.platform.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Objects;

/**
 * Zero Trust edge: every request is authenticated with a verified JWT, and every route has an explicit
 * scope rule. Anything not listed is denied (deny-by-default). No request is trusted because of where it
 * came from; "internal" callers present tokens too (client-credentials).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {

    public static final String[] PUBLIC_HEALTH = {
            "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness", "/actuator/info"};
    public static final String[] API_DOCS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemAuthenticationEntryPoint entryPoint,
                                    ProblemAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // Stateless bearer-token API: no cookies or sessions, so CSRF protection does not apply.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_HEALTH).permitAll()
                        .requestMatchers(API_DOCS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments").hasAuthority("SCOPE_payments:write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/*/cancel").hasAuthority("SCOPE_payments:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/*/saga").hasAuthority("SCOPE_payments:admin")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments", "/api/v1/payments/*")
                        .hasAuthority("SCOPE_payments:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasAuthority("SCOPE_accounts:write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/deposits").hasAuthority("SCOPE_funds:deposit")
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/freeze").hasAuthority("SCOPE_accounts:admin")
                        .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*").hasAuthority("SCOPE_accounts:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ledger/**").hasAuthority("SCOPE_ledger:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/fraud/**").hasAuthority("SCOPE_fraud:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/ops/dead-letters/replay").hasAuthority("SCOPE_ops:dlq-replay")
                        .requestMatchers("/api/v1/ops/manual-reviews", "/api/v1/ops/manual-reviews/**")
                        .hasAuthority("SCOPE_ops:manual-review")
                        .requestMatchers("/api/v1/ops/reconciliation/**").hasAuthority("SCOPE_ops:reconciliation")
                        .requestMatchers(HttpMethod.GET, "/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus")
                        .hasAuthority("SCOPE_ops:metrics")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * Production decoder: RS256 only (blocks {@code alg=none} and HS/RS key-confusion), keys from the IdP's
     * JWKS, fetched lazily so the service can start while the IdP is briefly down. Absent configuration means
     * no decoder bean and a failed startup, which is fail closed.
     */
    @Bean
    @ConditionalOnProperty(prefix = "payflow.security.jwt", name = "jwk-set-uri")
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(tokenValidator(properties));
        return decoder;
    }

    /** Claims every token must satisfy: shared by the production decoder and the security tests. */
    public static OAuth2TokenValidator<Jwt> tokenValidator(JwtProperties properties) {
        Objects.requireNonNull(properties.issuer(), "payflow.security.jwt.issuer must be set");
        Objects.requireNonNull(properties.audience(), "payflow.security.jwt.audience must be set");
        return new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(properties.clockSkew()),
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(properties.audience())),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, sub -> sub != null && !sub.isBlank())));
    }
}
