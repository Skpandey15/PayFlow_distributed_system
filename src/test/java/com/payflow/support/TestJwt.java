package com.payflow.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.payflow.platform.security.JwtProperties;
import com.payflow.platform.security.SecurityConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A throwaway RSA key pair standing in for the identity provider. The test {@link JwtDecoder} uses the
 * <em>same</em> claim validators as production ({@link SecurityConfiguration#tokenValidator}), so security
 * tests exercise real signature, issuer, audience and expiry checks rather than mocks.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwt {

    public static final String ISSUER = "https://idp.payflow.test/realms/payflow";
    public static final String AUDIENCE = "payflow-api";

    private static final RSAKey SIGNING_KEY = generateKey();

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(SecurityConfiguration.tokenValidator(properties));
        return decoder;
    }

    /** A valid RS256 token for {@code subject} with the given space-separated scopes. */
    public static String token(String subject, String scopes) {
        return token(claims -> claims.subject(subject).claim("scope", scopes));
    }

    /** A token with valid defaults that the customizer may break (wrong audience, expired, ...). */
    public static String token(Consumer<JWTClaimsSet.Builder> customizer) {
        JWTClaimsSet.Builder claims = defaultClaims();
        customizer.accept(claims);
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID()).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(SIGNING_KEY));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** An HS256 token (symmetric key confusion attempt). It must be rejected. */
    public static String hs256Token(String subject, String scopes) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                    defaultClaims().subject(subject).claim("scope", scopes).build());
            jwt.sign(new MACSigner(new byte[32]));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JWTClaimsSet.Builder defaultClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString());
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
