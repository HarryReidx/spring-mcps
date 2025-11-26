package com.example.ingest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ingest_task_logs")
public class IngestTaskLog {
    
    @Id
    private UUID id;
    
    @Column("task_id")
    private UUID taskId;
    
    @Column("log_level")
    private LogLevel logLevel;
    
    @Column("log_message")
    private String logMessage;
    
    @Column("log_detail")
    private String logDetail;
    
    @Column("created_at")
    private LocalDateTime createdAt;
    
    public enum LogLevel {
        INFO, WARN, ERROR
    }
}
