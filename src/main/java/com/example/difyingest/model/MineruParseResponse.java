package com.example.difyingest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * MinerU 解析响应 DTO
 * 实际格式：
 * {
 *   "backend": "pipeline",
 *   "version": "2.5.3",
 *   "results": {
 *     "文件名": {
 *       "md_content": "markdown内容..."
 *     }
 *   }
 * }
 */
@Data
public class MineruParseResponse {
    private String backend;
    private String version;
    private Map<String, DocumentResult> results;

    @Data
    public static class DocumentResult {
        @JsonProperty("md_content")
        private String mdContent;
    }
}
