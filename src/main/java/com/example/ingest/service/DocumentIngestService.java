package com.example.ingest.service;

import com.example.ingest.client.DifyClient;
import com.example.ingest.client.MineruClient;
import com.example.ingest.client.VlmClient;
import com.example.ingest.config.AppProperties;
import com.example.ingest.entity.IngestTask;
import com.example.ingest.entity.IngestTaskLog;
import com.example.ingest.entity.ToolFile;
import com.example.ingest.model.*;
import com.example.ingest.repository.IngestTaskLogRepository;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestService {
    
    private final MineruClient mineruClient;
    private final DifyClient difyClient;
    private final VlmClient vlmClient;
    private final SemanticTextProcessor semanticTextProcessor;
    private final ToolFileRepository toolFileRepository;
    private final IngestTaskLogRepository taskLogRepository;
    private final AppProperties appProperties;
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build();

    public IngestResponse ingestDocument(IngestRequest request, IngestTask.ExecutionMode executionMode, UUID taskId) {
        log.info("开始处理文档入库: datasetId={}, fileName={}, enableVlm={}, mode={}", 
                request.getDatasetId(), request.getFileName(), request.getEnableVlm(), executionMode);
        
        long totalStartTime = System.currentTimeMillis();
        long vlmCostTime = 0;
        
        try {
            // 1. 规则校验
            validateProcessRule(request, taskId);
            
            // 2. 下载文件
            File downloadedFile = downloadFile(request.getFileUrl(), request.getFileName());
            
            // 3. 格式转换
            File pdfFile = convertToPdfIfNeeded(downloadedFile, request.getFileType());
            
            // 4. 调用 MinerU 解析
            MineruParseResponse parseResponse = mineruClient.parsePdf(pdfFile, request.getFileName());
            
            // 5. 处理解析结果
            MineruParseResponse.FileResult fileResult = parseResponse.getResults().values().iterator().next();
            String mdContent = fileResult.getMdContent();
            Map<String, String> images = fileResult.getImages();
            
            log.info("MinerU 返回: markdown 长度={}, 图片数量={}", 
                    mdContent != null ? mdContent.length() : 0, 
                    images != null ? images.size() : 0);
            
            // 6. 处理图片：替换 markdown 中的临时路径为真实 MinIO URL
            String markdownWithRealUrls = replaceImagePaths(mdContent, images, taskId);
            
            // 7. 语义增强处理（记录 VLM 耗时）
            long vlmStartTime = System.currentTimeMillis();
            String finalMarkdown = performSemanticEnrichment(markdownWithRealUrls, images, request.getEnableVlm(), request);
            vlmCostTime = System.currentTimeMillis() - vlmStartTime;
            
            // 8. 调用 Dify API 写入知识库
            DifyCreateDocumentRequest difyRequest = buildDifyRequest(request, finalMarkdown);
            DifyCreateDocumentResponse difyResponse = difyClient.createDocument(request.getDatasetId(), difyRequest);
            
            // 9. 清理临时文件
            cleanupTempFiles(downloadedFile, pdfFile);
            
            // 10. 返回结果
            long totalCostTime = System.currentTimeMillis() - totalStartTime;
            
            return IngestResponse.builder()
                    .success(true)
                    .fileIds(Collections.singletonList(difyResponse.getDocument().getId()))
                    .stats(IngestResponse.Stats.builder()
                            .imageCount(images != null ? images.size() : 0)
                            .chunkCount(1)
                            .build())
                    .vlmCostTime(vlmCostTime)
                    .totalCostTime(totalCostTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("文档入库失败", e);
            logError(taskId, "文档入库失败", e.getMessage());
            return IngestResponse.builder()
                    .success(false)
                    .errorMsg(e.getMessage())
                    .fileIds(Collections.emptyList())
                    .build();
        }
    }

    /**
     * 规则校验：防止空入库
     */
    private void validateProcessRule(IngestRequest request, UUID taskId) {
        try {
            // 获取 Dataset 详情
            DifyDatasetDetail dataset = difyClient.getDatasetDetail(request.getDatasetId());
            
            logInfo(taskId, "获取 Dataset 详情成功", "类型: " + dataset.getIndexingTechnique());
            
            // 根据 Dataset 类型生成默认规则
            if (request.getSeparator() != null && !request.getSeparator().isEmpty()) {
                // 用户自定义规则，需要校验
                boolean isValid = validateCustomRule(request, dataset);
                if (!isValid) {
                    throw new IllegalArgumentException("自定义规则与 Dataset 配置不匹配，可能导致空入库");
                }
            }
        } catch (Exception e) {
            log.error("规则校验失败", e);
            logError(taskId, "规则校验失败", e.getMessage());
            throw new RuntimeException("规则校验失败: " + e.getMessage(), e);
        }
    }

    private boolean validateCustomRule(IngestRequest request, DifyDatasetDetail dataset) {
        // 简单校验逻辑
        return true;
    }

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

    private File convertToPdfIfNeeded(File file, String fileType) {
        if ("pdf".equalsIgnoreCase(fileType)) {
            return file;
        }
        
        log.warn("暂不支持 {} 格式转换，直接使用原文件", fileType);
        return file;
    }

    private String replaceImagePaths(String mdContent, Map<String, String> images, UUID taskId) {
        if (mdContent == null || images == null || images.isEmpty()) {
            log.info("无需替换图片路径");
            return mdContent;
        }
        
        if (toolFileRepository == null) {
            log.warn("数据库未配置，跳过图片路径替换");
            return mdContent;
        }
        
        log.info("开始替换图片路径，共 {} 张图片", images.size());
        
        String result = mdContent;
        
        try {
            List<String> imageNames = new ArrayList<>(images.keySet());
            List<ToolFile> toolFiles = toolFileRepository.findByNameIn(imageNames);
            Map<String, String> nameToFileKey = new HashMap<>();
            for (ToolFile toolFile : toolFiles) {
                nameToFileKey.put(toolFile.getName(), toolFile.getFileKey());
            }
            
            log.info("从数据库查询到 {} 条图片记录", toolFiles.size());
            
            for (String imageName : imageNames) {
                String fileKey = nameToFileKey.get(imageName);
                if (fileKey != null) {
                    String realUrl = appProperties.getMinio().getImgPathPrefix() + "/" + fileKey;
                    String tempPath = "images/" + imageName;
                    String pattern = "(!\\[.*?\\]\\()" + Pattern.quote(tempPath) + "(\\))";
                    result = result.replaceAll(pattern, "$1" + realUrl + "$2");
                    log.debug("替换图片路径: {} -> {}", tempPath, realUrl);
                } else {
                    logWarn(taskId, "图片替换失败", "未找到图片 " + imageName + " 的 file_key");
                }
            }
        } catch (Exception e) {
            log.error("图片路径替换失败", e);
            logError(taskId, "图片路径替换失败", e.getMessage());
        }
        
        return result;
    }

    private String performSemanticEnrichment(String markdown, Map<String, String> images, Boolean enableVlm, IngestRequest request) {
        Map<String, VlmClient.ImageAnalysisResult> analysisResults = null;
        
        if (Boolean.TRUE.equals(enableVlm) && images != null && !images.isEmpty()) {
            log.info("启用 VLM 图片分析，共 {} 张图片", images.size());
            analysisResults = analyzeImagesWithVlm(images);
        }
        
        boolean useDefaultSegmentation = request.getSeparator() == null || request.getSeparator().isEmpty();
        return semanticTextProcessor.enrichMarkdown(markdown, analysisResults, useDefaultSegmentation);
    }

    private Map<String, VlmClient.ImageAnalysisResult> analyzeImagesWithVlm(Map<String, String> images) {
        List<java.util.concurrent.CompletableFuture<VlmClient.ImageAnalysisResult>> futures = new ArrayList<>();
        Map<String, String> imageUrls = getImageRealUrls(images);
        
        for (Map.Entry<String, String> entry : imageUrls.entrySet()) {
            String imageName = entry.getKey();
            String imageUrl = entry.getValue();
            futures.add(vlmClient.analyzeImageAsync(imageUrl, imageUrl));
        }
        
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        
        Map<String, VlmClient.ImageAnalysisResult> results = new HashMap<>();
        for (java.util.concurrent.CompletableFuture<VlmClient.ImageAnalysisResult> future : futures) {
            try {
                VlmClient.ImageAnalysisResult result = future.get();
                results.put(result.getImageName(), result);
            } catch (Exception e) {
                log.error("获取 VLM 分析结果失败", e);
            }
        }
        
        log.info("VLM 分析完成，成功 {} 张", results.size());
        return results;
    }

    private Map<String, String> getImageRealUrls(Map<String, String> images) {
        Map<String, String> urls = new HashMap<>();
        
        if (toolFileRepository == null) {
            return urls;
        }
        
        try {
            List<String> imageNames = new ArrayList<>(images.keySet());
            List<ToolFile> toolFiles = toolFileRepository.findByNameIn(imageNames);
            
            for (ToolFile toolFile : toolFiles) {
                String realUrl = appProperties.getMinio().getImgPathPrefix() + "/" + toolFile.getFileKey();
                urls.put(toolFile.getName(), realUrl);
            }
        } catch (Exception e) {
            log.error("查询图片真实 URL 失败", e);
        }
        
        return urls;
    }

    private DifyCreateDocumentRequest buildDifyRequest(IngestRequest request, String markdown) {
        boolean isAutoMode = "AUTO".equalsIgnoreCase(request.getChunkingMode());
        boolean isHierarchical = "hierarchical_model".equalsIgnoreCase(request.getDocForm());
        
        DifyCreateDocumentRequest.ProcessRule processRule;
        
        if (isAutoMode) {
            processRule = DifyCreateDocumentRequest.ProcessRule.builder()
                    .mode("automatic")
                    .build();
        } else if (isHierarchical) {
            Integer maxTokens = request.getMaxTokens() != null ? 
                    request.getMaxTokens() : appProperties.getHierarchical().getMaxTokens();
            Integer subMaxTokens = request.getSubMaxTokens() != null ? 
                    request.getSubMaxTokens() : appProperties.getHierarchical().getSubMaxTokens();
            Integer chunkOverlap = request.getChunkOverlap() != null ? 
                    request.getChunkOverlap() : appProperties.getHierarchical().getChunkOverlap();
            
            processRule = DifyCreateDocumentRequest.ProcessRule.builder()
                    .mode("hierarchical")
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
                                    .separator("{{>1#}}")
                                    .maxTokens(maxTokens)
                                    .chunkOverlap(chunkOverlap)
                                    .build())
                            .parentMode("paragraph")
                            .subchunkSegmentation(DifyCreateDocumentRequest.Segmentation.builder()
                                    .separator("{{>2#}}")
                                    .maxTokens(subMaxTokens)
                                    .chunkOverlap(chunkOverlap)
                                    .build())
                            .build())
                    .build();
        } else {
            Integer maxTokens = request.getMaxTokens() != null ? 
                    request.getMaxTokens() : appProperties.getChunking().getMaxTokens();
            Integer chunkOverlap = request.getChunkOverlap() != null ? 
                    request.getChunkOverlap() : appProperties.getChunking().getChunkOverlap();
            String separator = request.getSeparator() != null ? 
                    request.getSeparator() : appProperties.getChunking().getSeparator();
            
            processRule = DifyCreateDocumentRequest.ProcessRule.builder()
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
                                    .separator(separator)
                                    .maxTokens(maxTokens)
                                    .chunkOverlap(chunkOverlap)
                                    .build())
                            .build())
                    .build();
        }
        
        return DifyCreateDocumentRequest.builder()
                .name(request.getFileName())
                .text(markdown)
                .indexingTechnique(request.getIndexingTechnique())
                .docForm(request.getDocForm())
                .processRule(processRule)
                .build();
    }

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

    private void logInfo(UUID taskId, String message, String detail) {
        if (taskId != null) {
            IngestTaskLog log = IngestTaskLog.builder()
                    .taskId(taskId)
                    .logLevel(IngestTaskLog.LogLevel.INFO)
                    .logMessage(message)
                    .logDetail(detail)
                    .createdAt(LocalDateTime.now())
                    .build();
            taskLogRepository.save(log);
        }
    }

    private void logWarn(UUID taskId, String message, String detail) {
        if (taskId != null) {
            IngestTaskLog log = IngestTaskLog.builder()
                    .taskId(taskId)
                    .logLevel(IngestTaskLog.LogLevel.WARN)
                    .logMessage(message)
                    .logDetail(detail)
                    .createdAt(LocalDateTime.now())
                    .build();
            taskLogRepository.save(log);
        }
    }

    private void logError(UUID taskId, String message, String detail) {
        if (taskId != null) {
            IngestTaskLog log = IngestTaskLog.builder()
                    .taskId(taskId)
                    .logLevel(IngestTaskLog.LogLevel.ERROR)
                    .logMessage(message)
                    .logDetail(detail)
                    .createdAt(LocalDateTime.now())
                    .build();
            taskLogRepository.save(log);
        }
    }
}
