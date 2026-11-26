package com.umar.integration.client;

import java.util.Map;

public interface WebhookHttpClient {

    HttpCallResult post(String targetUrl, Map<String, String> headers, String body, int timeoutMs);

    record HttpCallResult(
            String outcome, // SUCCESS | RECEIVER_ERROR | TIMEOUT | CONNECTION_FAILED | TLS_ERROR | REDIRECT_NOT_FOLLOWED
            Integer statusCode,
            long latencyMs,
            String responseSnippet,
            String redirectLocation,
            Integer retryAfterSeconds
    ) {
        public boolean is2xx() {
            return statusCode != null && statusCode >= 200 && statusCode < 300;
        }
    }
}
