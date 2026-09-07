package com.example.template.client;

import com.example.template.dto.ApiResponse;
import com.example.template.util.JsonHelper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import retrofit2.Response;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link RetrofitClientFactoryBean}, the Spring {@link org.springframework.beans.factory.FactoryBean}
 * that exposes {@link RetrofitClient}-annotated interfaces as Spring beans backed by Retrofit.
 *
 * <p>The host and timeouts are configured via a {@link MockEnvironment} — in production
 * they come from application.yml / Apollo.
 */
class RetrofitClientFactoryBeanTest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    @DisplayName("Factory bean resolves host + timeouts from Environment and routes through Retrofit")
    void factoryBeanBuildsWorkingProxy() throws Exception {
        String baseUrl = mockWebServer.url("/").toString();
        MockEnvironment env = new MockEnvironment();
        env.setProperty("clients.demo-client.host", baseUrl);
        env.setProperty("clients.demo-client.connect-timeout-ms", "1000");
        env.setProperty("clients.demo-client.read-timeout-ms", "2000");

        RetrofitClientFactoryBean<DemoClient> factoryBean = new RetrofitClientFactoryBean<>(DemoClient.class);
        factoryBean.setEnvironment(env);

        String responseBody = JsonHelper.toJson(ApiResponse.ok("pong"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        DemoClient client = factoryBean.getObject();
        Response<String> response = client.health().execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertTrue(response.body().contains("pong"));

        RecordedRequest recorded = mockWebServer.takeRequest();
        assertEquals("GET", recorded.getMethod());
        assertEquals("/api/health", recorded.getPath());
    }

    @Test
    @DisplayName("Missing host causes the factory bean to fail fast")
    void missingHostFailsFast() {
        MockEnvironment env = new MockEnvironment();
        RetrofitClientFactoryBean<DemoClient> factoryBean = new RetrofitClientFactoryBean<>(DemoClient.class);
        factoryBean.setEnvironment(env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, factoryBean::getObject);
        assertTrue(ex.getMessage().contains("demo-client"));
    }
}