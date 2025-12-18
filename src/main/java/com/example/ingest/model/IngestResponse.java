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

    private String errorMsg;
    
    private Long vlmCostTime;
    
    private Long mineruCostTime;
    
    private Long totalCostTime;
    
    private Long fileSize;
    
    private List<String> vlmFailedImages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private Integer imageCount;
        private Integer chunkCount;
    }
    
    public Boolean isSuccess() {
        return success;
    }
}
