package com.example.ingest.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Table("mcp_ingest_images")
public class IngestImage {
    @Id
    private UUID id;
    
    @Column("name")
    private String name;
    
    @Column("file_key")
    private String fileKey;
    
    @Column("minio_url")
    private String minioUrl;
    
    @Column("size")
    private Long size;
    
    @Column("mimetype")
    private String mimetype;
    
    @Column("created_at")
    private LocalDateTime createdAt;
    
    @Column("updated_at")
    private LocalDateTime updatedAt;
}
