package com.example.ingest.service;

import com.example.ingest.client.DifyClient;
import com.example.ingest.client.MineruClient;
import com.example.ingest.config.AppProperties;
import com.example.ingest.entity.ToolFile;
import com.example.ingest.model.*;
import com.example.ingest.repository.ToolFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 文档入库服务 - 完整实现 Dify 插件的图文解析流程
 */
@Slf4j
@Service
public class DocumentIngestService {
    
    private final MineruClient mineruClient;
    private final DifyClient difyClient;
    private final ToolFileRepository toolFileRepository;
    private final AppProperties appProperties;
    private final boolean dbEnabled;
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build();
    
    public DocumentIngestService(
            MineruClient mineruClient,
            DifyClient difyClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ToolFileRepository toolFileRepository,
            AppProperties appProperties) {
        this.mineruClient = mineruClient;
        this.difyClient = difyClient;
        this.toolFileRepository = toolFileRepository;
        this.appProperties = appProperties;
        this.dbEnabled = toolFileRepository != null;
        
        if (!dbEnabled) {
            log.warn("数据库未配置，图片路径替换功能将被禁用");
        }
    }

    /**
     * 主流程：下载 → 转换 → 解析 → 图片处理 → 入库
     */
    public IngestResponse ingestDocument(IngestRequest request) {
        log.info("开始处理文档入库: datasetId={}, fileName={}", request.getDatasetId(), request.getFileName());
        
        try {
            // 1. 下载文件
            File downloadedFile = downloadFile(request.getFileUrl(), request.getFileName());
            
            // 2. 格式转换（如果需要）
            File pdfFile = convertToPdfIfNeeded(downloadedFile, request.getFileType());
            
            // 3. 调用 MinerU 解析（获取 markdown + 图片）
            MineruParseResponse parseResponse = mineruClient.parsePdf(pdfFile, request.getFileName());
            
            // 4. 处理解析结果
            MineruParseResponse.FileResult fileResult = parseResponse.getResults().values().iterator().next();
            String mdContent = fileResult.getMdContent();
            Map<String, String> images = fileResult.getImages();
            
            log.info("MinerU 返回: markdown 长度={}, 图片数量={}", 
                    mdContent != null ? mdContent.length() : 0, 
                    images != null ? images.size() : 0);
            
            // 5. 处理图片：替换 markdown 中的临时路径为真实 MinIO URL
            String finalMarkdown = replaceImagePaths(mdContent, images);
            
            // 6. 调用 Dify API 写入知识库
            DifyCreateDocumentRequest difyRequest = buildDifyRequest(request.getFileName(), finalMarkdown);
            DifyCreateDocumentResponse difyResponse = difyClient.createDocument(request.getDatasetId(), difyRequest);
            
            // 7. 清理临时文件
            cleanupTempFiles(downloadedFile, pdfFile);
            
            // 8. 返回结果
            return IngestResponse.builder()
                    .success(true)
                    .fileIds(Collections.singletonList(difyResponse.getDocument().getId()))
                    .stats(IngestResponse.Stats.builder()
                            .imageCount(images != null ? images.size() : 0)
                            .chunkCount(1)
                            .build())
                    .build();
                    
        } catch (Exception e) {
            log.error("文档入库失败", e);
            return IngestResponse.builder()
                    .success(false)
                    .errorMsg(e.getMessage())
                    .fileIds(Collections.emptyList())
                    .build();
//            throw new RuntimeException("文档入库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载文件（支持大文件）
     */
    private File downloadFile(String fileUrl, String fileName) throws IOException {
        log.info("开始下载文件: {}", fileUrl);
        
        Request request = new Request.Builder()
                .url(fileUrl)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载文件失败: " + response.code());
            }
            
            File tempFile = Files.createTempFile("dify-ingest-", "-" + fileName).toFile();
            
            try (InputStream inputStream = response.body().byteStream();
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            
            log.info("文件下载完成: {}, 大小: {} bytes", tempFile.getAbsolutePath(), tempFile.length());
            return tempFile;
        }
    }

    /**
     * 格式转换（预留接口）
     * TODO: 实现 doc/docx/ppt/pptx → PDF 转换
     */
    private File convertToPdfIfNeeded(File file, String fileType) {
        if ("pdf".equalsIgnoreCase(fileType)) {
            return file;
        }
        
        // TODO: 使用 LibreOffice 或其他工具转换
        log.warn("暂不支持 {} 格式转换，直接使用原文件", fileType);
        return file;
    }

    /**
     * 核心：替换 markdown 中的图片路径
     * 参考 parse.py 的 _replace_md_img_path 方法
     * 
     * 流程：
     * 1. 从 MinerU 返回的 images 中提取图片文件名（如 "image_0.jpg"）
     * 2. 从 PostgreSQL tool_files 表查询对应的 file_key
     * 3. 拼接真实 MinIO URL: ${imgPathPrefix}/${file_key}
     * 4. 替换 markdown 中的 "images/image_0.jpg" 为真实 URL
     */
    private String replaceImagePaths(String mdContent, Map<String, String> images) {
        if (mdContent == null || images == null || images.isEmpty()) {
            log.info("无需替换图片路径");
            return mdContent;
        }
        
        if (!dbEnabled) {
            log.warn("数据库未配置，跳过图片路径替换，保留原始路径");
            return mdContent;
        }
        
        log.info("开始替换图片路径，共 {} 张图片", images.size());
        
        String result = mdContent;
        
        try {
            // 提取所有图片文件名
            List<String> imageNames = new ArrayList<>(images.keySet());
            
            // 从数据库查询 file_key
            List<ToolFile> toolFiles = toolFileRepository.findByNameIn(imageNames);
            Map<String, String> nameToFileKey = new HashMap<>();
            for (ToolFile toolFile : toolFiles) {
                nameToFileKey.put(toolFile.getName(), toolFile.getFileKey());
            }
            
            log.info("从数据库查询到 {} 条图片记录", toolFiles.size());
            
            // 替换 markdown 中的图片路径
            // 使用正则表达式精确匹配 Markdown 图片语法: ![alt](images/xxx.jpg)
            for (String imageName : imageNames) {
                String fileKey = nameToFileKey.get(imageName);
                if (fileKey != null) {
                    String realUrl = appProperties.getMinio().getImgPathPrefix() + "/" + fileKey;
                    String tempPath = "images/" + imageName;
                    
                    // 使用正则替换，保留 Markdown 图片语法
                    // 匹配: ![任意内容](images/xxx.jpg) 或 ![](images/xxx.jpg)
                    String pattern = "(!\\[.*?\\]\\()" + Pattern.quote(tempPath) + "(\\))";
                    result = result.replaceAll(pattern, "$1" + realUrl + "$2");
                    
                    log.debug("替换图片路径: {} -> {}", tempPath, realUrl);
                } else {
                    log.warn("未找到图片 {} 的 file_key，跳过替换", imageName);
                }
            }
        } catch (Exception e) {
            log.error("图片路径替换失败，使用原始 markdown", e);
        }
        
        return result;
    }

    /**
     * 构建 Dify 创建文档请求
     */
    private DifyCreateDocumentRequest buildDifyRequest(String fileName, String markdown) {
        return DifyCreateDocumentRequest.builder()
                .name(fileName)
                .text(markdown)
                .indexingTechnique("high_quality")
                .processRule(DifyCreateDocumentRequest.ProcessRule.builder()
                        .mode("custom")
                        .rules(DifyCreateDocumentRequest.Rules.builder()
                                .preProcessingRules(new DifyCreateDocumentRequest.PreProcessingRule[]{
                                        DifyCreateDocumentRequest.PreProcessingRule.builder()
                                                .id("remove_extra_spaces")
                                                .enabled(true)
                                                .build(),
                                        DifyCreateDocumentRequest.PreProcessingRule.builder()
                                                .id("remove_urls_emails")
                                                .enabled(false)
                                                .build()
                                })
                                .segmentation(DifyCreateDocumentRequest.Segmentation.builder()
                                        .separator(appProperties.getChunking().getSeparator())
                                        .maxTokens(appProperties.getChunking().getMaxTokens())
                                        .chunkOverlap(appProperties.getChunking().getChunkOverlap())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    /**
     * 清理临时文件
     */
    private void cleanupTempFiles(File... files) {
        for (File file : files) {
            if (file != null && file.exists()) {
                try {
                    Files.delete(file.toPath());
                    log.debug("删除临时文件: {}", file.getAbsolutePath());
                } catch (IOException e) {
                    log.warn("删除临时文件失败: {}", file.getAbsolutePath(), e);
                }
            }
        }
    }
}
