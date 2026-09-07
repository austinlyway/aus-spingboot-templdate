package com.example.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifies the Spring application context loads successfully against
 * the H2 in-memory database configured in {@code src/test/resources/application.yml}.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationTest {

    @Test
    @DisplayName("Application context loads")
    void contextLoads() {
        // empty — success means wiring is OK
    }
}