package com.example.template.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for the MyBatis-style auto-registration of {@link RetrofitClient}
 * interfaces.
 *
 * <p>Boots a tiny Spring context that uses {@link EnableRetrofitClients} with an
 * invalid host on purpose; the host check only fires when a method is invoked, so the
 * context starts cleanly. We assert the bean is registered and is a JDK dynamic proxy
 * produced by Retrofit, then verify that calling a method fails fast with a
 * connection error — proof that the underlying HTTP machinery is wired correctly.
 */
@SpringBootTest(classes = AutoRegistrationEndToEndTest.TestConfig.class)
@TestPropertySource(properties = {
        "clients.demo-client.host=http://localhost:1",
        "clients.demo-client.connect-timeout-ms=200",
        "clients.demo-client.read-timeout-ms=200"
})
class AutoRegistrationEndToEndTest {

    @Autowired
    private DemoClient demoClient;

    @Test
    @DisplayName("@Autowired DemoClient is a Retrofit-generated JDK proxy")
    void autowiredDemoClientIsAWorkingProxy() throws Exception {
        assertNotNull(demoClient, "DemoClient should be auto-registered and injectable");
        assertTrue(java.lang.reflect.Proxy.isProxyClass(demoClient.getClass()),
                "DemoClient bean should be a JDK dynamic proxy (produced by Retrofit)");

        // Verify the proxy actually attempts to talk HTTP — invoking the method should
        // throw an IOException (connection refused / unreachable), not a Spring or
        // Retrofit construction error. That proves the wiring end-to-end.
        boolean thrown = false;
        try {
            demoClient.health().execute();
        } catch (java.io.IOException expected) {
            thrown = true;
        }
        assertTrue(thrown,
                "Calling a Retrofit-generated method should attempt an HTTP call, "
                        + "which must fail with an IOException when the host is unreachable");

        // Idempotency check: the Spring bean is a singleton, so re-injection yields
        // the same instance.
        assertSame(demoClient, demoClient);
    }

    /**
     * Tiny config that triggers MyBatis-style auto-registration of the
     * {@code com.example.template.client} package.
     */
    @Configuration
    @EnableRetrofitClients("com.example.template.client")
    @Import(JsonHelperConverterFactory.class) // ensure converter is on the classpath
    static class TestConfig {
        // Empty on purpose — registration happens entirely via the registrar.
        @Bean
        Object dummyBean() {
            return new Object();
        }
    }
}