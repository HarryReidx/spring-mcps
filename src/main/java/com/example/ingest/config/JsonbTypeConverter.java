package com.example.ingest.config;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/**
 * 枚举类型转换器
 * 用于 Spring Data JDBC 与 PostgreSQL 类型的转换
 */
public class JsonbTypeConverter {
    
    /**
     * 写入转换器：TaskStatus -> String
     */
    @Component
    @WritingConverter
    public static class TaskStatusToStringConverter implements Converter<com.example.ingest.entity.IngestTask.TaskStatus, String> {
        @Override
        public String convert(com.example.ingest.entity.IngestTask.TaskStatus source) {
            return source.name();
        }
    }
    
    /**
     * 读取转换器：String -> TaskStatus
     */
    @Component
    @ReadingConverter
    public static class StringToTaskStatusConverter implements Converter<String, com.example.ingest.entity.IngestTask.TaskStatus> {
        @Override
        public com.example.ingest.entity.IngestTask.TaskStatus convert(String source) {
            return com.example.ingest.entity.IngestTask.TaskStatus.valueOf(source);
        }
    }
    
    /**
     * 写入转换器：ExecutionMode -> String
     */
    @Component
    @WritingConverter
    public static class ExecutionModeToStringConverter implements Converter<com.example.ingest.entity.IngestTask.ExecutionMode, String> {
        @Override
        public String convert(com.example.ingest.entity.IngestTask.ExecutionMode source) {
            return source.name();
        }
    }
    
    /**
     * 读取转换器：String -> ExecutionMode
     */
    @Component
    @ReadingConverter
    public static class StringToExecutionModeConverter implements Converter<String, com.example.ingest.entity.IngestTask.ExecutionMode> {
        @Override
        public com.example.ingest.entity.IngestTask.ExecutionMode convert(String source) {
            return com.example.ingest.entity.IngestTask.ExecutionMode.valueOf(source);
        }
    }
}
