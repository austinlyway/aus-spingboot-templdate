package com.example.template.client;

import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables auto-registration of {@link RetrofitClient}-annotated interfaces as Spring
 * beans backed by Retrofit.
 *
 * <p>Modelled after MyBatis's {@code @MapperScan} — declare it once on a
 * {@code @Configuration} class and every interface annotated with
 * {@link RetrofitClient} under {@link #basePackages()} will be picked up.
 *
 * <p>Example:
 * <pre>
 * &#64;Configuration
 * &#64;EnableRetrofitClients(basePackages = "com.example.template.client")
 * public class RetrofitClientsConfig { }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(RetrofitClientRegistrar.class)
public @interface EnableRetrofitClients {

    /**
     * Packages to scan for {@link RetrofitClient}-annotated interfaces. Alias for
     * {@link #basePackages()}.
     */
    String[] value() default {};

    /**
     * Packages to scan. May be combined with {@link #basePackageClasses()} — both
     * are honoured and deduplicated.
     */
    String[] basePackages() default {};

    /**
     * Type-safe alternative to {@link #basePackages()}: every listed class's package
     * is scanned.
     */
    Class<?>[] basePackageClasses() default {};
}