package com.payflow.platform.security;

import com.payflow.support.Actors;
import com.payflow.support.ApiClient;
import com.payflow.support.IntegrationTest;
import com.payflow.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Zero Trust evidence: verify explicitly (signature, issuer, audience, expiry, algorithm), least privilege
 * (per-route scopes, deny by default), and object-level authorisation (no BOLA).
 */
@IntegrationTest
class SecurityIT {

    @Autowired
    MockMvc mvc;
    ApiClient api;
    String aliceSubject;
    String alice;
    String bob;
    UUID aliceAccount;
    UUID bobAccount;
    UUID alicePayment;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        aliceSubject = "alice-" + UUID.randomUUID();
        alice = TestJwt.token(aliceSubject, Actors.CUSTOMER_SCOPES);
        bob = TestJwt.token("bob-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES);
        aliceAccount = api.openAccount(alice, "USD");
        bobAccount = api.openAccount(bob, "USD");
        alicePayment = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), aliceAccount,
                bobAccount, "1.00", "USD", "CARD", null));
    }

    @Nested
    class VerifyExplicitly {

        @Test
        void missingTokenIsChallenged() throws Exception {
            mvc.perform(get("/api/v1/payments/{id}", alicePayment))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.correlationId").exists());
        }

        @Test
        void validTokenIsAccepted() throws Exception {
            api.getPayment(alice, alicePayment).andExpect(status().isOk());
        }

        @Test
        void expiredTokenIsRejected() throws Exception {
            String expired = TestJwt.token(c -> c.subject(aliceSubject).claim("scope", Actors.CUSTOMER_SCOPES)
                    .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                    .notBeforeTime(Date.from(Instant.now().minusSeconds(3600)))
                    .expirationTime(Date.from(Instant.now().minusSeconds(120))));
            api.getPayment(expired, alicePayment).andExpect(status().isUnauthorized());
        }

        @Test
        void tokenForAnotherAudienceIsRejected() throws Exception {
            String otherApi = TestJwt.token(c -> c.subject(aliceSubject).claim("scope", Actors.CUSTOMER_SCOPES)
                    .audience(List.of("some-other-api")));
            api.getPayment(otherApi, alicePayment).andExpect(status().isUnauthorized());
        }

        @Test
        void tokenFromAnotherIssuerIsRejected() throws Exception {
            String foreign = TestJwt.token(c -> c.subject(aliceSubject).claim("scope", Actors.CUSTOMER_SCOPES)
                    .issuer("https://evil.example/realms/payflow"));
            api.getPayment(foreign, alicePayment).andExpect(status().isUnauthorized());
        }

        @Test
        void symmetricallySignedTokenIsRejected() throws Exception {
            api.getPayment(TestJwt.hs256Token(aliceSubject, Actors.CUSTOMER_SCOPES), alicePayment)
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void tamperedTokenIsRejected() throws Exception {
            String[] parts = alice.split("\\.");
            String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ("{\"sub\":\"" + aliceSubject + "\",\"scope\":\"payments:admin payments:read\",\"iss\":\""
                            + TestJwt.ISSUER + "\",\"aud\":\"" + TestJwt.AUDIENCE + "\",\"exp\":9999999999}").getBytes());
            api.getPayment(parts[0] + "." + forgedPayload + "." + parts[2], alicePayment)
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void tokenWithoutSubjectIsRejected() throws Exception {
            String anonymous = TestJwt.token(c -> c.claim("scope", Actors.CUSTOMER_SCOPES));
            api.getPayment(anonymous, alicePayment).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class LeastPrivilege {

        @Test
        void customersCannotDriveThePaymentLifecycle() throws Exception {
            api.authorize(alice, alicePayment, "US")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
            api.process(alice, alicePayment).andExpect(status().isForbidden());
        }

        @Test
        void readOnlyTokenCannotCreatePayments() throws Exception {
            String readOnly = TestJwt.token(aliceSubject, "payments:read");
            api.createPayment(readOnly, UUID.randomUUID().toString(), aliceAccount, bobAccount, "1.00", "USD", "CARD", null)
                    .andExpect(status().isForbidden());
        }

        @Test
        void processorIdentityCannotReadCustomerPayments() throws Exception {
            api.getPayment(TestJwt.token("svc-orchestrator", Actors.PROCESSOR_SCOPES), alicePayment)
                    .andExpect(status().isForbidden());
        }

        @Test
        void unmappedRoutesAreDeniedByDefault() throws Exception {
            mvc.perform(get("/api/v1/internal/anything").header("Authorization", ApiClient.bearer(alice)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class ObjectLevelAuthorization {

        @Test
        void otherCustomersCannotSeeOrCancelAPayment() throws Exception {
            api.getPayment(bob, alicePayment)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
            api.cancel(bob, alicePayment).andExpect(status().isNotFound());
            api.getPayment(alice, alicePayment).andExpect(jsonPath("$.status").value("CREATED"));
        }

        @Test
        void customersCannotPayFromSomeoneElsesAccount() throws Exception {
            api.createPayment(bob, UUID.randomUUID().toString(), aliceAccount, bobAccount, "1.00", "USD", "CARD", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PAYER_ACCOUNT_NOT_FOUND"));
        }

        @Test
        void customersCannotReadOtherCustomersAccounts() throws Exception {
            mvc.perform(get("/api/v1/accounts/{id}", aliceAccount).header("Authorization", ApiClient.bearer(bob)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void listingOnlyReturnsTheCallersOwnPayments() throws Exception {
            mvc.perform(get("/api/v1/payments").header("Authorization", ApiClient.bearer(bob)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(0));
        }

        @Test
        void adminsCanReadAnyPayment() throws Exception {
            String admin = TestJwt.token("support-1", "payments:read payments:admin");
            api.getPayment(admin, alicePayment).andExpect(status().isOk());
        }
    }
}
