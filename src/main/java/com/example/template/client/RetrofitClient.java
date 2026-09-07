package com.example.template.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as a Retrofit-based HTTP client.
 *
 * <p>The {@link #clientName()} must match a key under {@code clients.<name>.host}
 * in application.yml / Apollo.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RetrofitClient {

    /**
     * Identifier used to look up the configured host under {@code clients.<name>.host}.
     */
    String clientName();
}