package com.example.ingest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DifyCreateDocumentRequest {
    private String name;
    private String text;
    
    @JsonProperty("indexing_technique")
    private String indexingTechnique;
    
    @JsonProperty("process_rule")
    private ProcessRule processRule;
    
    @JsonProperty("doc_form")
    private String docForm;

    @Data
    @Builder
    public static class ProcessRule {
        private String mode;  // automatic, custom, hierarchical
        private Rules rules;
    }

    @Data
    @Builder
    public static class Rules {
        @JsonProperty("pre_processing_rules")
        private PreProcessingRule[] preProcessingRules;
        
        private Segmentation segmentation;
        
        @JsonProperty("parent_mode")
        private String parentMode;  // paragraph, full-doc
        
        @JsonProperty("subchunk_segmentation")
        private Segmentation subchunkSegmentation;  // 子分段配置
    }

    @Data
    @Builder
    public static class PreProcessingRule {
        private String id;
        private Boolean enabled;
    }

    @Data
    @Builder
    public static class Segmentation {
        private String separator;
        
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        
        @JsonProperty("chunk_overlap")
        private Integer chunkOverlap;
    }
}
