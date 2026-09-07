package com.example.template.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * MyBatis-style auto-registrar for {@link RetrofitClient}-annotated interfaces.
 *
 * <p>Triggered by {@link EnableRetrofitClients}. Scans the configured base packages
 * for interfaces annotated with {@link RetrofitClient} and registers a
 * {@link RetrofitClientFactoryBean} for each one. The factory bean builds a Retrofit
 * proxy at first injection and reads host + timeouts from the
 * {@code clients.<clientName>.*} block (sourced from application.yml / Apollo).
 *
 * <p>Equivalent to MyBatis's {@code @MapperScan} — once you declare
 * {@code @EnableRetrofitClients(basePackages = "com.example.template.client")} on a
 * configuration class, adding a new client is just:
 * <ol>
 *   <li>Create a new interface annotated with {@code @RetrofitClient(clientName = "...")}.</li>
 *   <li>Add a matching {@code clients.<clientName>.*} block in application.yml / Apollo.</li>
 * </ol>
 * No Java registration code is needed.
 */
@Slf4j
public class RetrofitClientRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        AnnotationAttributes attrs = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableRetrofitClients.class.getName()));
        if (attrs == null) {
            return;
        }

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);
        Set<String> basePackages = resolveBasePackages(attrs);
        int registered = 0;

        for (String basePackage : basePackages) {
            Set<String> candidates = scanBasePackage(resolver, readerFactory, basePackage);
            log.debug("Scanned package '{}' — {} candidate(s): {}", basePackage, candidates.size(), candidates);
            for (String className : candidates) {
                try {
                    Class<?> type = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
                    if (!type.isInterface()) {
                        log.warn("@RetrofitClient can only be applied to interfaces; skipping {}", className);
                        continue;
                    }
                    registerClientBean(registry, type);
                    registered++;
                } catch (ClassNotFoundException e) {
                    log.warn("Could not load scanned class {}", className, e);
                }
            }
        }
        log.info("Auto-registered {} Retrofit client interface(s) from {}", registered, basePackages);
    }

    /**
     * Scan the given base package for classes annotated with {@link RetrofitClient}.
     * Uses raw classpath scanning so that interfaces (which Spring's default
     * {@code ClassPathScanningCandidateComponentProvider} rejects) are picked up too.
     */
    private Set<String> scanBasePackage(ResourcePatternResolver resolver,
                                        MetadataReaderFactory readerFactory,
                                        String basePackage) {
        Set<String> matches = new LinkedHashSet<>();
        String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                ClassUtils.convertClassNameToResourcePath(basePackage) + "/**/*.class";
        try {
            for (Resource resource : resolver.getResources(pattern)) {
                MetadataReader reader = readerFactory.getMetadataReader(resource);
                if (reader.getAnnotationMetadata().hasAnnotation(RetrofitClient.class.getName())) {
                    matches.add(reader.getClassMetadata().getClassName());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan package '{}'", basePackage, e);
        }
        return matches;
    }

    /**
     * Build the set of base packages to scan, honouring both {@code basePackages}
     * (string list) and {@code basePackageClasses} (class list — convenience marker).
     * The {@code value} alias is treated the same as {@code basePackages}.
     */
    private Set<String> resolveBasePackages(AnnotationAttributes attrs) {
        Set<String> packages = new LinkedHashSet<>();
        for (String pkg : attrs.getStringArray("value")) {
            if (!pkg.isBlank()) {
                packages.add(pkg);
            }
        }
        for (String pkg : attrs.getStringArray("basePackages")) {
            if (!pkg.isBlank()) {
                packages.add(pkg);
            }
        }
        for (Class<?> marker : attrs.getClassArray("basePackageClasses")) {
            packages.add(ClassUtils.getPackageName(marker));
        }
        return packages;
    }

    /**
     * Register a {@link RetrofitClientFactoryBean} for the given service interface.
     * The factory bean's constructor argument is the service Class, so the factory
     * bean's {@code getObjectType()} returns the interface type — which lets Spring
     * expose the bean under the interface name and resolve {@code @Autowired}
     * injections directly on the interface.
     */
    private void registerClientBean(BeanDefinitionRegistry registry, Class<?> serviceInterface) {
        String beanName = beanNameFor(serviceInterface);
        if (registry.containsBeanDefinition(beanName)) {
            log.warn("Skipping {} — a bean named '{}' is already registered",
                    serviceInterface.getName(), beanName);
            return;
        }
        RootBeanDefinition definition = new RootBeanDefinition(RetrofitClientFactoryBean.class);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(0, serviceInterface);
        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        definition.setAttribute("retrofitClient", Boolean.TRUE);
        registry.registerBeanDefinition(beanName, definition);
        log.debug("Registered Retrofit client bean '{}' -> {}", beanName, serviceInterface.getName());
    }

    /**
     * Lower-camel-case the simple class name — mirrors Spring's default naming so
     * {@code @Qualifier("demoClient")} works without surprises.
     */
    static String beanNameFor(Class<?> serviceInterface) {
        String simple = serviceInterface.getSimpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }
}