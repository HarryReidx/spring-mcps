package com.example.difyingest.model;

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

    @Data
    @Builder
    public static class ProcessRule {
        private String mode;
        private Rules rules;
    }

    @Data
    @Builder
    public static class Rules {
        @JsonProperty("pre_processing_rules")
        private Object[] preProcessingRules;
        private Segmentation segmentation;
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
