package com.example.template.config;

import com.example.template.util.JsonHelper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Reads the configured JSON implementation from {@code application.yml}
 * and binds it to {@link JsonHelper} before any bean uses it.
 */
@Slf4j
@Configuration
public class JsonConfig {

    @Value("${json.implementation:jackson}")
    private String implementation;

    @PostConstruct
    public void init() {
        try {
            JsonHelper.setAdapterByName(implementation);
            log.info("JSON implementation bound: {}", JsonHelper.activeImplementation());
        } catch (Exception e) {
            log.warn("Failed to bind JSON implementation '{}', falling back to default", implementation, e);
            JsonHelper.setAdapterByName("jackson");
        }
    }
}