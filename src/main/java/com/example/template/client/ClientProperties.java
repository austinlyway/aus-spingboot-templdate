package com.example.template.client;

import lombok.Data;

/**
 * Configuration for a single external HTTP client.
 *
 * <p>Bound from {@code clients.<clientName>.*} keys in application.yml and (transparently
 * through Spring {@link org.springframework.core.env.Environment}) from Apollo at runtime.
 * Each property — host, timeouts, logging — lives here so there is a single place to look
 * for a client's network configuration.
 *
 * <p>Example {@code application.yml}:
 * <pre>
 * clients:
 *   demo-client:
 *     host: http://localhost:9090
 *     connect-timeout-ms: 5000
 *     read-timeout-ms: 10000
 *     write-timeout-ms: 5000
 *     enable-logging: true
 * </pre>
 */
@Data
public class ClientProperties {

    /** Base URL for the client, e.g. {@code http://example.com:8080}. */
    private String host;

    /** Connect timeout in milliseconds. */
    private long connectTimeoutMs = 5_000L;

    /** Read timeout in milliseconds. */
    private long readTimeoutMs = 10_000L;

    /** Write timeout in milliseconds. */
    private long writeTimeoutMs = 5_000L;

    /** Whether to enable OkHttp's logging interceptor at DEBUG. */
    private boolean enableLogging = true;
}