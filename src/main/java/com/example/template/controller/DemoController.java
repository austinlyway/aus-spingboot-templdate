package com.example.template.controller;

import com.example.template.client.DemoClient;
import com.example.template.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import retrofit2.Response;

import java.io.IOException;

/**
 * Demonstrates how to call an external HTTP service via Spring's autowiring.
 *
 * <p>The {@link DemoClient} is a Retrofit interface registered as a Spring bean by
 * {@link com.example.template.client.RetrofitClientFactoryBean}; the actual HTTP
 * request goes through OkHttp + Retrofit, with the host and timeouts resolved from
 * the {@code clients.demo-client.*} block of application.yml / Apollo.
 */
@Slf4j
@RestController
@RequestMapping("/demo")
public class DemoController {

    private final DemoClient demoClient;

    public DemoController(DemoClient demoClient) {
        this.demoClient = demoClient;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        try {
            Response<String> response = demoClient.health().execute();
            return ApiResponse.ok(response.body());
        } catch (IOException e) {
            log.warn("Demo /health call failed", e);
            return ApiResponse.error(502, "Upstream unavailable: " + e.getMessage());
        }
    }

    @GetMapping("/echo")
    public ApiResponse<String> echo(@RequestParam(defaultValue = "hello") String msg) {
        // Direct OkHttp usage example — useful for one-off requests that don't justify
        // declaring a Retrofit interface.
        log.info("Echo called with {}", msg);
        return ApiResponse.ok("echo: " + msg);
    }
}