package com.example.template.config;

import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * MyBatis-related Spring configuration.
 *
 * <p>The {@link DataSource} is auto-configured by Spring Boot from {@code spring.datasource.*}
 * properties; this class only adds the {@link SqlSessionFactoryBean} (with mapper XML
 * locations and type aliases) and a {@link PlatformTransactionManager}.
 *
 * <p>Mappers under {@code com.example.template.mapper} are scanned via {@link MapperScan}
 * — and additionally at the application class level.
 */
@Configuration
@MapperScan("com.example.template.mapper")
public class MyBatisConfig {

    @Bean
    public SqlSessionFactoryBean sqlSessionFactoryBean(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/*.xml"));
        factory.setTypeAliasesPackage("com.example.template.entity");
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        factory.setConfiguration(configuration);
        return factory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}