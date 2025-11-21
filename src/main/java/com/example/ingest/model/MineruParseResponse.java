package com.example.ingest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MineruParseResponse {
    private Map<String, FileResult> results;

    @Data
    public static class FileResult {
        @JsonProperty("md_content")
        private String mdContent;
        
        @JsonProperty("content_list")
        private String contentList;
        
        @JsonProperty("images")
        private Map<String, String> images;
    }
}
