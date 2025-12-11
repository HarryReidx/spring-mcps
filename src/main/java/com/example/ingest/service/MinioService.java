package com.example.ingest.service;

import com.example.ingest.config.AppProperties;
import com.example.ingest.repository.IngestImageRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.UUID;

/**
 * MinIO 文件上传服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {
    
    private final AppProperties appProperties;
    private final IngestImageRepository ingestImageRepository;
    private MinioClient minioClient;
    
    private MinioClient getMinioClient() {
        if (minioClient == null) {
            AppProperties.MinioConfig config = appProperties.getMinio();
            minioClient = MinioClient.builder()
                    .endpoint(config.getEndpoint())
                    .credentials(config.getAccessKey(), config.getSecretKey())
                    .region(config.getRegion())
                    .build();
        }
        return minioClient;
    }
    
    /**
     * 上传 base64 图片到 MinIO
     * 
     * @param imageName 图片名称
     * @param base64Data base64 编码的图片数据
     * @return MinIO 文件路径
     */
    public String uploadImage(String imageName, String base64Data) {
        try {
            // 移除 data:image/xxx;base64, 前缀和所有空白字符
            String cleanBase64 = base64Data;
            if (base64Data.contains(",")) {
                cleanBase64 = base64Data.substring(base64Data.indexOf(",") + 1);
            }
            cleanBase64 = cleanBase64.replaceAll("\\s+", "");
            
            byte[] imageBytes = Base64.getDecoder().decode(cleanBase64);
            String uploadPath = appProperties.getMinio().getUploadPath();
            String fileKey = uploadPath + UUID.randomUUID() + "_" + imageName;
            
            getMinioClient().putObject(
                    PutObjectArgs.builder()
                            .bucket(appProperties.getMinio().getBucketName())
                            .object(fileKey)
                            .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                            .contentType(getContentType(imageName))
                            .build()
            );
            
            // 保存到数据库
            String mimetype = getContentType(imageName);
            String minioUrl = appProperties.getMinio().getImgPathPrefix() + "/" + fileKey;
            ingestImageRepository.insertImage(UUID.randomUUID(), imageName, fileKey, minioUrl, imageBytes.length, mimetype);
            
            log.debug("图片上传成功: {} -> {}", imageName, fileKey);
            return fileKey;
        } catch (Exception e) {
            log.error("图片上传失败: {}", imageName, e);
            throw new RuntimeException("图片上传失败: " + e.getMessage(), e);
        }
    }
    
    private String getContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
