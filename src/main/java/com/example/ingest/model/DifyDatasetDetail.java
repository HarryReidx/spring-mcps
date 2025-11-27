package com.example.ingest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Dify Dataset 详情
 * 
 * @author HarryReid(黄药师)
 */
@Data
public class DifyDatasetDetail {
    /** Dataset ID */
    private String id;
    
    /** Dataset 名称 */
    private String name;
    
    /** 索引技术: high_quality, economy */
    @JsonProperty("indexing_technique")
    private String indexingTechnique;
    
    /** 文档形式: text_model, qa_model, hierarchical_model */
    @JsonProperty("doc_form")
    private String docForm;
    
    /** 权限 */
    private String permission;
}
