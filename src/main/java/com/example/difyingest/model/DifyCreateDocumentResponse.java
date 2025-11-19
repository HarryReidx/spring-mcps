package com.example.difyingest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DifyCreateDocumentResponse {
    private Document document;
    
    // batch 可能是字符串或对象，统一处理为字符串
    private String batch;

    @Data
    public static class Document {
        private String id;
        private Integer position;
        
        @JsonProperty("data_source_type")
        private String dataSourceType;
        
        @JsonProperty("data_source_info")
        private DataSourceInfo dataSourceInfo;
        
        @JsonProperty("dataset_process_rule_id")
        private String datasetProcessRuleId;
        
        private String name;
        
        @JsonProperty("created_from")
        private String createdFrom;
        
        @JsonProperty("created_by")
        private String createdBy;
        
        @JsonProperty("created_at")
        private Long createdAt;
    }

    @Data
    public static class DataSourceInfo {
        @JsonProperty("upload_file_id")
        private String uploadFileId;
    }
}
