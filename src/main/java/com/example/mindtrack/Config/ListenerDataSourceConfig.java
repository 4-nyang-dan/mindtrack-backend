package com.example.mindtrack.Config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ListenerDataSourceConfig {

    @Bean
    @ConfigurationProperties("listener-datasource")
    public DataSourceProperties listenerDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "listenerDataSource")
    @ConfigurationProperties("listener-datasource.hikari")
    public DataSource listenerDataSource() {
        return listenerDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
