package com.example.difyingest.repository;

import com.example.difyingest.model.ToolFile;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ToolFileRepository extends CrudRepository<ToolFile, UUID> {

    /**
     * 根据 ID 列表查询文件信息
     * 使用 CAST 将字符串转换为 UUID 类型
     */
    @Query("SELECT id, file_key FROM tool_files WHERE CAST(id AS TEXT) = ANY(:ids)")
    List<ToolFile> findByIdIn(@Param("ids") String[] ids);
}
