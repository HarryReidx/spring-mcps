package com.example.ingest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MineruParseRequest {
    @JsonProperty("parse_method")
    private String parseMethod;
    
    @JsonProperty("return_md")
    private Boolean returnMd;
    
    @JsonProperty("return_model_output")
    private Boolean returnModelOutput;
    
    @JsonProperty("return_content_list")
    private Boolean returnContentList;
    
    @JsonProperty("lang_list")
    private String[] langList;
    
    @JsonProperty("return_images")
    private Boolean returnImages;
    
    @JsonProperty("backend")
    private String backend;
    
    @JsonProperty("formula_enable")
    private Boolean formulaEnable;
    
    @JsonProperty("table_enable")
    private Boolean tableEnable;
    
    @JsonProperty("server_url")
    private String serverUrl;
    
    @JsonProperty("return_middle_json")
    private Boolean returnMiddleJson;
}
