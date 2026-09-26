package com.payflow.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Thin HTTP helper for API tests. Every call carries a real signed bearer token. */
public record ApiClient(MockMvc mvc) {

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

    public ResultActions createPayment(String token, String idempotencyKey, UUID payer, UUID payee, String amount,
                                       String currency, String method, String reference) throws Exception {
        String json = """
                {"payerAccountId":"%s","payeeAccountId":"%s","amount":"%s","currency":"%s","method":"%s","reference":%s}"""
                .formatted(payer, payee, amount, currency, method, reference == null ? "null" : "\"" + reference + "\"");
        return mvc.perform(post("/api/v1/payments").header("Authorization", bearer(token))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public UUID createdPaymentId(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    public ResultActions authorize(String token, UUID paymentId, String countryCode) throws Exception {
        return mvc.perform(post("/api/v1/payments/{id}/authorize", paymentId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\":\"dev-1\",\"ipAddress\":\"203.0.113.9\",\"countryCode\":\"" + countryCode + "\"}"));
    }

    public ResultActions process(String token, UUID paymentId) throws Exception {
        return mvc.perform(post("/api/v1/payments/{id}/process", paymentId).header("Authorization", bearer(token)));
    }

    public ResultActions cancel(String token, UUID paymentId) throws Exception {
        return mvc.perform(post("/api/v1/payments/{id}/cancel", paymentId).header("Authorization", bearer(token)));
    }

    public ResultActions getPayment(String token, UUID paymentId) throws Exception {
        return mvc.perform(get("/api/v1/payments/{id}", paymentId).header("Authorization", bearer(token)));
    }
}
