package com.example.ingest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档入库响应
 * 返回给 Dify HTTP 插件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {
    /** 是否成功 */
    private Boolean success;
    
    /** 文档 ID 列表 */
    private List<String> fileIds;
    
    /** 统计信息 */
    private Stats stats;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 统计信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        /** 图片数量 */
        private Integer imageCount;
        
        /** 分段数量 */
        private Integer chunkCount;
    }
    // 是否成功
    public Boolean isSuccess() {
        return success;
    }
}
