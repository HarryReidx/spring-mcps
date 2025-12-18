package com.example.ingest.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 数据源配置
 * 配置连接池重试机制，防止启动时数据库未就绪导致应用崩溃
 */
@Slf4j
@Configuration
public class DataSourceConfig {
    
    @Bean
    @Primary
    public DataSource dataSource(org.springframework.boot.autoconfigure.jdbc.DataSourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        
        // 连接池配置
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);  // 30s
        config.setIdleTimeout(600000);       // 10min
        config.setMaxLifetime(1800000);      // 30min
        
        // 连接测试
        config.setConnectionTestQuery("SELECT 1");
        
        // 重试机制：启动时尝试连接数据库，失败则重试
        int maxRetries = 5;
        int retryDelay = 3000;  // 3s
        
        for (int i = 1; i <= maxRetries; i++) {
            try {
                log.info("尝试连接数据库 (第 {}/{} 次)...", i, maxRetries);
                HikariDataSource dataSource = new HikariDataSource(config);
                // 测试连接
                dataSource.getConnection().close();
                log.info("数据库连接成功");
                return dataSource;
            } catch (Exception e) {
                log.warn("数据库连接失败 (第 {}/{} 次): {}", i, maxRetries, e.getMessage());
                if (i == maxRetries) {
                    log.error("数据库连接失败，已达最大重试次数", e);
                    throw new RuntimeException("无法连接到数据库，请检查配置和网络", e);
                }
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("数据库连接重试被中断", ie);
                }
            }
        }
        
        throw new RuntimeException("数据库连接失败");
    }
}
