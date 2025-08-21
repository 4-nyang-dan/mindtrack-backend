package com.example.mindtrack.Config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class MainDataSourceConfig {

    @Bean
    @Primary // 메인 풀을 항상 기본으로 쓰게 강제
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mainDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataSource") // Hibernate/JPA는 이 빈을 기본으로 씀
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource mainDataSource() {
        return mainDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .build();
    }
}
