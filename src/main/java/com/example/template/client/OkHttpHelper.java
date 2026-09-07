package com.example.template.client;

import com.example.template.common.TypeReference;
import com.example.template.util.JsonHelper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Thin helper around OkHttp for ad-hoc calls that don't need a typed Retrofit
 * interface. Host and timeouts are read from the same {@code clients.<clientName>*}
 * block as the Retrofit-typed clients, so configuration stays in one place.
 *
 * <p>For typed APIs, prefer declaring a Retrofit interface annotated with
 * {@link RetrofitClient} and letting {@link RetrofitClientFactoryBean} register it
 * as a Spring bean for direct injection.
 */
@Slf4j
@Component
public class OkHttpHelper {

    private final Environment environment;

    public OkHttpHelper(Environment environment) {
        this.environment = environment;
    }

    /**
     * Perform a GET against {@code clients.<clientName>.host + path} and decode the
     * response into {@code type}.
     */
    public <T> T get(String clientName, String path, Class<T> type) throws IOException {
        Request request = new Request.Builder()
                .url(url(clientName, path))
                .get()
                .build();
        try (Response response = okHttp(clientName).newCall(request).execute()) {
            return JsonHelper.fromJson(response.body().string(), type);
        }
    }

    public <T> T get(String clientName, String path, TypeReference<T> typeReference) throws IOException {
        Request request = new Request.Builder()
                .url(url(clientName, path))
                .get()
                .build();
        try (Response response = okHttp(clientName).newCall(request).execute()) {
            return JsonHelper.fromJson(response.body().string(), typeReference);
        }
    }

    /**
     * POST a JSON-serialized body and deserialize the response into {@code type}.
     */
    public <T> T post(String clientName, String path, Object body, Class<T> type) throws IOException {
        Request request = new Request.Builder()
                .url(url(clientName, path))
                .post(RequestBody.create(JsonHelper.toJson(body),
                        okhttp3.MediaType.get("application/json; charset=utf-8")))
                .build();
        try (Response response = okHttp(clientName).newCall(request).execute()) {
            return JsonHelper.fromJson(response.body().string(), type);
        }
    }

    public <T> T post(String clientName, String path, Object body, TypeReference<T> typeReference) throws IOException {
        Request request = new Request.Builder()
                .url(url(clientName, path))
                .post(RequestBody.create(JsonHelper.toJson(body),
                        okhttp3.MediaType.get("application/json; charset=utf-8")))
                .build();
        try (Response response = okHttp(clientName).newCall(request).execute()) {
            return JsonHelper.fromJson(response.body().string(), typeReference);
        }
    }

    /**
     * Execute a raw request and return the response as a String (for debugging).
     */
    public String executeRaw(Request request) throws IOException {
        try (Response response = okHttp(request.url().host()).newCall(request).execute()) {
            return response.body() != null ? response.body().string() : "";
        }
    }

    /**
     * Resolve the configured host for {@code clientName}, falling back to the literal
     * {@code path} when no client config exists (so this helper also works for
     * fully-qualified URLs).
     */
    private String url(String clientName, String path) {
        String host = environment.getProperty("clients." + clientName + ".host");
        if (host == null || host.isBlank()) {
            throw new IllegalStateException(
                    "No host configured for client '" + clientName + "' (expected 'clients." + clientName + ".host')");
        }
        return host + path;
    }

    /**
     * Build an {@link OkHttpClient} for the given {@code clientName} using that
     * client's timeouts. The OkHttp client is created per call to keep the API
     * stateless — connection pooling still happens inside each client, so this is
     * cheap.
     */
    private OkHttpClient okHttp(String clientName) {
        ClientProperties props = new ClientProperties();
        String prefix = "clients." + clientName + ".";
        props.setHost(environment.getProperty(prefix + "host"));
        props.setConnectTimeoutMs(parseLong(environment.getProperty(prefix + "connect-timeout-ms"),
                props.getConnectTimeoutMs()));
        props.setReadTimeoutMs(parseLong(environment.getProperty(prefix + "read-timeout-ms"),
                props.getReadTimeoutMs()));
        props.setWriteTimeoutMs(parseLong(environment.getProperty(prefix + "write-timeout-ms"),
                props.getWriteTimeoutMs()));
        return new OkHttpClient.Builder()
                .connectTimeout(props.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(props.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(props.getWriteTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    private static long parseLong(String s, long fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    /**
     * Convenience: build a request with default headers.
     */
    public static Request.Builder newRequest(String url) {
        return new Request.Builder().url(url);
    }

    /**
     * Convert a simple map to query string and append to a URL.
     */
    public static String withQueryParams(String baseUrl, Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }
        StringBuilder sb = new StringBuilder(baseUrl);
        sb.append(baseUrl.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
}