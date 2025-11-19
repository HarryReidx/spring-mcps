package com.example.difyingest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Dify dify = new Dify();
    private Mineru mineru = new Mineru();
    private Img img = new Img();
    private Segmentation segmentation = new Segmentation();

    @Data
    public static class Dify {
        private String baseUrl;
        private String apiKey;
    }

    @Data
    public static class Mineru {
        private String baseUrl;
    }

    @Data
    public static class Img {
        private String pathPrefix;
    }

    @Data
    public static class Segmentation {
        private String separator;
        private Integer maxTokens;
        private Integer chunkOverlap;
    }
}
