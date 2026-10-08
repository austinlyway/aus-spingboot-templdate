package com.example.template.config;

import com.example.template.entity.User;
import com.example.template.mybatis.SingleEntityMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for {@link SingleEntityMapper} beans.
 *
 * <p>Generic Spring beans can't be created from {@code @Component} alone (the
 * generic type parameter {@code T} is erased at runtime), so each entity gets
 * its own {@code @Bean} method below. Add a new bean whenever you want a
 * {@code SingleEntityMapper} for a new entity.
 *
 * <p>Usage:
 * <pre>
 * &#64;Autowired private SingleEntityMapper&lt;User&gt; userMapper;
 * </pre>
 */
@Configuration
public class SingleEntityMapperConfig {

    /**
     * The single-entity CRUD support for {@link User}. Inject this anywhere you
     * need type-safe CRUD without creating a {@code @Mapper} interface.
     */
    @Bean
    public SingleEntityMapper<User> userSingleEntityMapper(SqlSessionFactory factory) {
        return new SingleEntityMapper<>(User.class, factory);
    }
}