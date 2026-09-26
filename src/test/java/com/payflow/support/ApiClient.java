package com.payflow.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Thin HTTP helper for API tests. Every call carries a real signed bearer token. */
public record ApiClient(MockMvc mvc) {

    public static final String TREASURY = TestJwt.token("treasury-1", "funds:deposit");
    public static final String OPS = TestJwt.token("ops-1", "payments:read payments:admin accounts:read accounts:admin ledger:read fraud:read");

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public UUID openAccount(String token, String currency) throws Exception {
        String body = mvc.perform(post("/api/v1/accounts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Main\",\"currency\":\"" + currency + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    public ResultActions deposit(UUID accountId, UUID depositId, String amount, String currency) throws Exception {
        return mvc.perform(post("/api/v1/accounts/{id}/deposits", accountId).header("Authorization", bearer(TREASURY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"depositId\":\"" + depositId + "\",\"amount\":\"" + amount + "\",\"currency\":\"" + currency + "\"}"));
    }

    public UUID fundedAccount(String token, String currency, String amount) throws Exception {
        UUID account = openAccount(token, currency);
        deposit(account, UUID.randomUUID(), amount, currency).andExpect(status().isCreated());
        return account;
    }

    public ResultActions createPayment(String token, String idempotencyKey, UUID payer, UUID payee, String amount,
                                       String currency, String method, String reference) throws Exception {
        return createPayment(token, idempotencyKey, payer, payee, amount, currency, method, reference, "US");
    }

    public ResultActions createPayment(String token, String idempotencyKey, UUID payer, UUID payee, String amount,
                                       String currency, String method, String reference, String country) throws Exception {
        String json = """
                {"payerAccountId":"%s","payeeAccountId":"%s","amount":"%s","currency":"%s","method":"%s","reference":%s,
                 "checkout":{"deviceId":"dev-1","ipAddress":"203.0.113.9","countryCode":"%s"}}"""
                .formatted(payer, payee, amount, currency, method, reference == null ? "null" : "\"" + reference + "\"",
                        country);
        return mvc.perform(post("/api/v1/payments").header("Authorization", bearer(token))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public UUID createdPaymentId(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    public ResultActions cancel(String token, UUID paymentId) throws Exception {
        return mvc.perform(post("/api/v1/payments/{id}/cancel", paymentId).header("Authorization", bearer(token)));
    }

    public ResultActions getPayment(String token, UUID paymentId) throws Exception {
        return mvc.perform(get("/api/v1/payments/{id}", paymentId).header("Authorization", bearer(token)));
    }

    public String paymentField(String token, UUID paymentId, String jsonPath) throws Exception {
        return JsonPath.read(getPayment(token, paymentId).andReturn().getResponse().getContentAsString(), jsonPath);
    }

    /** The saga is asynchronous: poll until the payment reaches {@code expectedStatus}. */
    public void awaitStatus(String token, UUID paymentId, String expectedStatus) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> expectedStatus.equals(paymentField(token, paymentId, "$.status")));
    }

    public String accountField(UUID accountId, String jsonPath) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/accounts/{id}", accountId).header("Authorization", bearer(OPS)))
                .andReturn().getResponse().getContentAsString(), jsonPath);
    }

    public String ledgerBalance(UUID accountId, String currency) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/ledger/accounts/{id}/balance", accountId).param("currency", currency)
                .header("Authorization", bearer(OPS))).andReturn().getResponse().getContentAsString(), "$.amount");
    }

    public String sagaStep(UUID paymentId) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/payments/{id}/saga", paymentId).header("Authorization", bearer(OPS)))
                .andReturn().getResponse().getContentAsString(), "$.step");
    }
}
