package com.example.ingest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 应用配置属性
 * 映射 application.yml 中的 app.* 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    /** Dify API 配置 */
    private DifyConfig dify = new DifyConfig();
    
    /** MinerU 服务配置 */
    private MineruConfig mineru = new MineruConfig();
    
    /** MinIO 存储配置 */
    private MinioConfig minio = new MinioConfig();
    
    /** 文档分段配置 */
    private ChunkingConfig chunking = new ChunkingConfig();
    
    /** 父子分段配置 */
    private HierarchicalConfig hierarchical = new HierarchicalConfig();
    
    /** VLM 视觉模型配置 */
    private VlmConfig vlm = new VlmConfig();

    /**
     * Dify API 配置
     */
    @Data
    public static class DifyConfig {
        /** API 密钥 */
        private String apiKey;
        
        /** API 基础地址 */
        private String baseUrl;
    }

    /**
     * MinerU 服务配置
     */
    @Data
    public static class MineruConfig {
        /** 服务地址 */
        private String baseUrl;
        
        /** 服务类型: local 或 remote */
        private String serverType = "local";
        
        /** 远程服务 token（可选） */
        private String token;
        
        /** 解析方法: auto, ocr, txt */
        private String parseMethod = "auto";
        
        /** 解析后端: pipeline, vlm-transformers 等 */
        private String backend = "pipeline";
        
        /** 是否启用公式识别 */
        private Boolean enableFormula = true;
        
        /** 是否启用表格识别 */
        private Boolean enableTable = true;
        
        /** 文档语言: ch, en 等 */
        private String language = "ch";
    }

    /**
     * MinIO 存储配置
     */
    @Data
    public static class MinioConfig {
        /** 图片路径前缀 */
        private String imgPathPrefix;
    }

    /**
     * 文档分段配置（文本模型）
     */
    @Data
    public static class ChunkingConfig {
        /** 分段分隔符 */
        private String separator = "\n";
        
        /** 最大 token 数 */
        private Integer maxTokens = 1000;
        
        /** 分段重叠 */
        private Integer chunkOverlap = 50;
    }

    /**
     * 父子分段配置（层级模型）
     */
    @Data
    public static class HierarchicalConfig {
        /** 父分段最大 token 数 */
        private Integer maxTokens = 1024;
        
        /** 子分段最大 token 数 */
        private Integer subMaxTokens = 512;
        
        /** 分段重叠 token 数 */
        private Integer chunkOverlap = 50;
    }

    /**
     * VLM 视觉模型配置
     */
    @Data
    public static class VlmConfig {
        /** 提供商类型: openai, ollama（可选，自动检测） */
        private String provider;
        
        /** API 基础地址 */
        private String baseUrl = "https://api.openai.com/v1/chat/completions";
        
        /** API 密钥（Ollama 不需要） */
        private String apiKey;
        
        /** 模型名称 */
        private String model = "gpt-4o";
        
        /** 最大 token 数 */
        private Integer maxTokens = 1000;
        
        /** 提示词 */
        private String prompt = "请详细描述这张图片的内容，并提取其中的所有文字。格式：描述: [图片描述]\nOCR: [提取的文字]";
    }
}
