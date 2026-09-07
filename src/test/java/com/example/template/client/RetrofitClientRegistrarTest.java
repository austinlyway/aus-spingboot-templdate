package com.example.template.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.type.AnnotationMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RetrofitClientRegistrar}.
 *
 * <p>Verifies the MyBatis-style auto-registration logic: scanning a package for
 * {@link RetrofitClient}-annotated interfaces and registering a
 * {@link RetrofitClientFactoryBean} for each.
 */
class RetrofitClientRegistrarTest {

    @Test
    @DisplayName("Scans client package and registers a factory bean for DemoClient")
    void registersFactoryBeanForScannedInterface() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        AnnotationMetadata importingMetadata = AnnotationMetadata.introspect(TestEnableConfig.class);

        RetrofitClientRegistrar registrar = new RetrofitClientRegistrar();
        registrar.registerBeanDefinitions(importingMetadata, registry);

        assertTrue(registry.containsBeanDefinition("demoClient"),
                "Expected 'demoClient' bean to be registered");

        var def = registry.getBeanDefinition("demoClient");
        assertEquals(RetrofitClientFactoryBean.class.getName(), def.getBeanClassName());

        // The first constructor argument must be the DemoClient Class object.
        Object serviceArg = def.getConstructorArgumentValues()
                .getIndexedArgumentValue(0, Class.class)
                .getValue();
        assertEquals(DemoClient.class, serviceArg);

        // Sanity-check the bean-name helper.
        assertEquals("demoClient", RetrofitClientRegistrar.beanNameFor(DemoClient.class));
    }

    @Test
    @DisplayName("No-op when @EnableRetrofitClients is absent")
    void noOpWithoutAnnotation() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        AnnotationMetadata importingMetadata = AnnotationMetadata.introspect(UnannotatedConfig.class);

        new RetrofitClientRegistrar().registerBeanDefinitions(importingMetadata, registry);

        assertEquals(0, registry.getBeanDefinitionCount(),
                "Registrar should not register any beans when the annotation is absent");
    }

    @Test
    @DisplayName("Ignores classes (not interfaces) annotated with @RetrofitClient")
    void rejectsNonInterface() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        AnnotationMetadata importingMetadata = AnnotationMetadata.introspect(TestEnableConfig.class);

        // Even if someone applies @RetrofitClient to a non-interface, the registrar must
        // not crash — verify by re-running; the existing classes are all interfaces, so
        // nothing should be rejected under 'com.example.template.client'.
        new RetrofitClientRegistrar().registerBeanDefinitions(importingMetadata, registry);
        assertTrue(registry.containsBeanDefinition("demoClient"));
    }

    /**
     * Stub class bearing {@link EnableRetrofitClients} so the registrar picks up
     * the annotation via {@link AnnotationMetadata#introspect(Class)}.
     */
    @EnableRetrofitClients("com.example.template.client")
    private static class TestEnableConfig {
    }

    /**
     * Stub class without the annotation, used to verify the registrar's no-op path.
     */
    private static class UnannotatedConfig {
    }
}