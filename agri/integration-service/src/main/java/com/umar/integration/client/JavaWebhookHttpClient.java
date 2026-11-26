package com.umar.integration.client;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;

@Component
public class JavaWebhookHttpClient implements WebhookHttpClient {

    private static final Pattern SECRET_SNIPPET_MASK = Pattern.compile(
            "(?i)(whsec_[A-Za-z0-9_\\-]+|Bearer\\s+[A-Za-z0-9._\\-]+|vt_[a-z]+_[A-Za-z0-9]+|sk_[A-Za-z0-9]+)"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile Function<DispatchRequest, HttpCallResult> testStub;

    public void setTestStub(Function<DispatchRequest, HttpCallResult> stub) {
        this.testStub = stub;
    }

    public void clearTestStub() {
        this.testStub = null;
    }

    @Override
    public HttpCallResult post(String targetUrl, Map<String, String> headers, String body, int timeoutMs) {
        Function<DispatchRequest, HttpCallResult> stub = this.testStub;
        if (stub != null) {
            return stub.apply(new DispatchRequest(targetUrl, headers, body, timeoutMs));
        }

        long start = System.currentTimeMillis();
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(reqBuilder::header);

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            int status = response.statusCode();

            if (status >= 300 && status < 400) {
                String loc = response.headers().firstValue("Location").orElse(null);
                return new HttpCallResult("REDIRECT_NOT_FOLLOWED", status, latency,
                        maskSnippet(response.body()), loc, null);
            }
            Integer retryAfter = parseRetryAfter(response.headers().firstValue("Retry-After"));
            String outcome = (status >= 200 && status < 300) ? "SUCCESS" : "RECEIVER_ERROR";
            return new HttpCallResult(outcome, status, latency, maskSnippet(response.body()), null, retryAfter);
        } catch (HttpTimeoutException e) {
            return new HttpCallResult("TIMEOUT", null, System.currentTimeMillis() - start, null, null, null);
        } catch (SSLException e) {
            return new HttpCallResult("TLS_ERROR", null, System.currentTimeMillis() - start,
                    maskSnippet(e.getMessage()), null, null);
        } catch (Exception e) {
            return new HttpCallResult("CONNECTION_FAILED", null, System.currentTimeMillis() - start,
                    maskSnippet(e.getMessage()), null, null);
        }
    }

    public static String maskSnippet(String raw) {
        if (raw == null) return null;
        String truncated = raw.length() > 2048 ? raw.substring(0, 2048) : raw;
        return SECRET_SNIPPET_MASK.matcher(truncated).replaceAll("***MASKED***");
    }

    private Integer parseRetryAfter(Optional<String> value) {
        if (value.isEmpty()) return null;
        try {
            int sec = Integer.parseInt(value.get().trim());
            return Math.min(Math.max(sec, 0), 3600);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record DispatchRequest(String targetUrl, Map<String, String> headers, String body, int timeoutMs) {}
}
