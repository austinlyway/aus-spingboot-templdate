package com.example.template;

import com.example.template.client.EnableRetrofitClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point for the Spring Boot template.
 *
 * <p>{@link EnableRetrofitClients} triggers MyBatis-style auto-registration of every
 * {@link com.example.template.client.RetrofitClient}-annotated interface in the
 * {@code com.example.template.client} package.
 *
 * <p>Mapper scanning lives on {@link com.example.template.config.MyBatisConfig} so that
 * slice tests (like {@code @WebMvcTest}) can opt out of the data layer via
 * {@code excludeFilters} without needing to suppress the scan.
 */
@SpringBootApplication
@EnableRetrofitClients("com.example.template.client")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}