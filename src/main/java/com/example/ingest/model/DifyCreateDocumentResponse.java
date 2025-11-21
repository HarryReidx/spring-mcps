package com.example.ingest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DifyCreateDocumentResponse {
    private DocumentData document;
    private String batch;  // Dify API 返回的是字符串，不是对象

    @Data
    public static class DocumentData {
        private String id;
        private String name;
    }
}
