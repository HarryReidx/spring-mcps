package com.example.ingest.repository;

import com.example.ingest.entity.IngestImage;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IngestImageRepository extends CrudRepository<IngestImage, UUID> {
    
    @Query("SELECT * FROM mcp_ingest_images WHERE name IN (:names)")
    List<IngestImage> findByNameIn(@Param("names") List<String> names);
    
    @org.springframework.data.jdbc.repository.query.Modifying
    @Query("INSERT INTO mcp_ingest_images (id, name, file_key, minio_url, size, mimetype, created_at, updated_at) " +
           "VALUES (:id, :name, :fileKey, :minioUrl, :size, :mimetype, NOW(), NOW())")
    void insertImage(@Param("id") UUID id, 
                     @Param("name") String name, 
                     @Param("fileKey") String fileKey,
                     @Param("minioUrl") String minioUrl,
                     @Param("size") long size,
                     @Param("mimetype") String mimetype);
}
