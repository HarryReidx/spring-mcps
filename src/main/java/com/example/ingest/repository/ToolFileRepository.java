package com.example.ingest.repository;

import com.example.ingest.entity.ToolFile;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * tool_files 表数据访问层
 * 用于查询图片文件的存储信息
 */
@Repository
public interface ToolFileRepository extends CrudRepository<ToolFile, UUID> {
    
    /**
     * 根据文件名查询
     * @param name 文件名
     * @return 文件信息
     */
    @Query("SELECT * FROM tool_files WHERE name = :name")
    Optional<ToolFile> findByName(@Param("name") String name);
    
    /**
     * 批量查询文件信息
     * @param names 文件名列表
     * @return 文件信息列表
     */
    @Query("SELECT * FROM tool_files WHERE name IN (:names)")
    List<ToolFile> findByNameIn(@Param("names") List<String> names);
    
    /**
     * 插入文件记录
     */
    @org.springframework.data.jdbc.repository.query.Modifying
    @Query("INSERT INTO tool_files (id, name, file_key, user_id, tenant_id, mimetype, size) VALUES (:id, :name, :fileKey, '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid, :mimetype, :size)")
    void insertToolFile(@Param("id") UUID id, @Param("name") String name, @Param("fileKey") String fileKey, @Param("mimetype") String mimetype, @Param("size") int size);
}
