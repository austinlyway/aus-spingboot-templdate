package com.example.template.config;

import lombok.Data;

/**
 * Properties bound to the {@code app.apollo.*} keys. The actual Apollo bootstrap is
 * enabled by Spring Boot's {@code apollo-spring-boot-starter} (if you add it) or by
 * {@code @EnableApolloConfig} on a config class. This template documents the expected
 * keys so they can be referenced from any module without typos.
 *
 * <p>Typical Apollo-related keys:
 * <pre>
 * app:
 *   id: spring-boot-template
 *   apollo:
 *     meta: http://apollo.meta.server
 *     bootstrap:
 *       enabled: true
 *       namespaces: application,common
 *     cluster: default
 * </pre>
 */
@Data
public class ApolloConfig {

    private String id = "spring-boot-template";
    private Bootstrap bootstrap = new Bootstrap();
    private String meta = "http://localhost:8080";
    private String cluster = "default";

    @Data
    public static class Bootstrap {
        private boolean enabled = false;
        private String namespaces = "application";
    }
}