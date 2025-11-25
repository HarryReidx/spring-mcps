package com.example.ingest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import java.util.Arrays;
import java.util.List;

/**
 * JDBC 配置
 * 注册自定义类型转换器
 */
@Configuration
@EnableJdbcRepositories(basePackages = "com.example.ingest.repository")
public class JdbcConfig extends AbstractJdbcConfiguration {

    @Override
    protected List<?> userConverters() {
        return Arrays.asList(
                // 枚举类型转换器
                new JsonbTypeConverter.TaskStatusToStringConverter(),
                new JsonbTypeConverter.StringToTaskStatusConverter(),
                new JsonbTypeConverter.ExecutionModeToStringConverter(),
                new JsonbTypeConverter.StringToExecutionModeConverter()
        );
    }
}
