package com.payflow.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 with the RFC 6750 {@code WWW-Authenticate} challenge plus a problem-details body. The body never says
 * <em>why</em> a token was rejected (expired, wrong audience, bad signature), so the response does not help
 * an attacker probe the validator. The reason is logged by Spring Security at DEBUG.
 */
@Component
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint challenge = new BearerTokenAuthenticationEntryPoint();
    private final ProblemResponseWriter writer;

    ProblemAuthenticationEntryPoint(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        challenge.commence(request, response, e);
        writer.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid bearer token is required");
    }
}
