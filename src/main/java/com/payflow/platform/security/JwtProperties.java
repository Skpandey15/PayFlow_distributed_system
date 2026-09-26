package com.payflow.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Token validation settings. There are no secrets here: tokens are verified with the IdP's public JWKS.
 *
 * @param issuer     exact expected {@code iss}; tokens from any other issuer are rejected
 * @param audience   required {@code aud} entry; tokens minted for another API are rejected (token confusion)
 * @param jwkSetUri  where to fetch signing keys (fetched lazily and cached, rotation-friendly)
 * @param clockSkew  tolerated clock drift for exp/nbf
 */
@ConfigurationProperties("payflow.security.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        String jwkSetUri,
        @DefaultValue("30s") Duration clockSkew) {
}
