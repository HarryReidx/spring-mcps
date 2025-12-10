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
import com.example.ingest.util.TextCleaningUtils;
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

/**
 * 文档入库服务
 * 核心业务逻辑：文件下载、MinerU 解析、VLM 增强、Dify 入库
 * 
 * @author HarryReid(黄药师)
 */
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
    private final MinioService minioService;
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(1800, TimeUnit.SECONDS)  // 30分钟，解决大文件下载超时
            .build();

    /**
     * 文档入库主流程
     * 
     * @param request 入库请求
     * @param executionMode 执行模式（SYNC/ASYNC）
     * @param taskId 任务 ID
     * @param dataset Dataset 详情
     * @return 入库结果
     */
    public IngestResponse ingestDocument(IngestRequest request, IngestTask.ExecutionMode executionMode, UUID taskId, DifyDatasetDetail dataset) {
        log.info("开始处理文档入库: datasetId={}, fileName={}, enableVlm={}, mode={}", 
                request.getDatasetId(), request.getFileName(), request.getEnableVlm(), executionMode);
        
        long totalStartTime = System.currentTimeMillis();
        long vlmCostTime = 0;
        
        try {
            // 1. 记录 Dataset 信息
            logInfo(taskId, "开始处理文档", 
                    String.format("indexingTechnique=%s, docForm=%s", 
                            dataset.getIndexingTechnique(), dataset.getDocForm()));

            // 2. 下载文件并记录大小
            File downloadedFile = downloadFile(request.getFileUrl(), request.getFileName());
            long fileSize = downloadedFile.length();
            logInfo(taskId, "文件下载完成", String.format("大小: %d bytes", fileSize));
            
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
            
            // 5.0 清洗 Markdown 文本（修复康熙部首问题）
            mdContent = TextCleaningUtils.cleanText(mdContent);
            log.info("文本清洗完成，清洗后长度={}", mdContent != null ? mdContent.length() : 0);
            
            // 5.1 保存 Markdown 到本地（如果启用）
            saveMdToTempIfEnabled(mdContent, request.getFileName(), taskId);
            
            // 6. 处理图片：替换 markdown 中的临时路径为真实 MinIO URL
            String markdownWithRealUrls = replaceImagePaths(mdContent, images, taskId);
            
            // 7. 语义增强处理
            long vlmStartTime = System.currentTimeMillis();
            String finalMarkdown = performSemanticEnrichment(markdownWithRealUrls, getImageRealUrls(images), request.getEnableVlm(), dataset);
            vlmCostTime = System.currentTimeMillis() - vlmStartTime;
            
            // 8. 调用 Dify API 写入知识库
            DifyCreateDocumentRequest difyRequest = buildDifyRequest(request, finalMarkdown, dataset);
            DifyCreateDocumentResponse difyResponse = difyClient.createDocument(request.getDatasetId(), difyRequest);
            
            // 9. 清理临时文件
            cleanupTempFiles(downloadedFile, pdfFile);
            
            // 10. 返回结果
            long totalCostTime = System.currentTimeMillis() - totalStartTime;
            
            IngestResponse response = IngestResponse.builder()
                    .success(true)
                    .fileIds(Collections.singletonList(difyResponse.getDocument().getId()))
                    .stats(IngestResponse.Stats.builder()
                            .imageCount(images != null ? images.size() : 0)
                            .chunkCount(1)
                            .build())
                    .vlmCostTime(vlmCostTime)
                    .totalCostTime(totalCostTime)
                    .fileSize(fileSize)
                    .build();
            
            return response;
                    
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
     * 前置校验：查询 Dataset 配置并校验规则
     * 
     * @param request 入库请求
     * @param taskId 任务 ID
     * @return Dataset 详情
     */
    public DifyDatasetDetail validateProcessRule(IngestRequest request, UUID taskId) {
        try {
            DifyDatasetDetail dataset = difyClient.getDatasetDetail(request.getDatasetId());
            
            logInfo(taskId, "获取 Dataset 详情成功", 
                    String.format("indexingTechnique=%s, docForm=%s", 
                            dataset.getIndexingTechnique(), dataset.getDocForm()));
            
            // CUSTOM 模式下校验规则兼容性（修正拼写错误）
            String chunkingMode = request.getChunkingMode();
            if (chunkingMode != null && 
                (chunkingMode.equalsIgnoreCase("CUSTOM") || chunkingMode.equalsIgnoreCase("CONSUTOM"))) {
                if (request.getSeparator() == null || request.getSeparator().isEmpty()) {
                    throw new IllegalArgumentException("CUSTOM 模式下必须指定 separator");
                }
            }
            
            return dataset;
        } catch (Exception e) {
            log.error("规则校验失败", e);
            logError(taskId, "规则校验失败", e.getMessage());
            throw new RuntimeException("规则校验失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载文件到本地临时目录
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
     * 格式转换（如需要）
     */
    private File convertToPdfIfNeeded(File file, String fileType) {
        if ("pdf".equalsIgnoreCase(fileType)) {
            return file;
        }
        
        log.warn("暂不支持 {} 格式转换，直接使用原文件", fileType);
        return file;
    }

    /**
     * 替换 Markdown 中的图片路径为真实 MinIO URL
     */
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
            
            // 如果数据库无记录，尝试上传图片到 MinIO
            if (toolFiles.isEmpty() && !imageNames.isEmpty()) {
                log.info("数据库无图片记录，开始上传到 MinIO");
                logInfo(taskId, "开始上传图片", String.format("共 %d 张图片", imageNames.size()));
                
                int successCount = 0;
                for (String imageName : imageNames) {
                    try {
                        String base64Data = images.get(imageName);
                        String fileKey = minioService.uploadImage(imageName, base64Data);
                        nameToFileKey.put(imageName, fileKey);
                        successCount++;
                    } catch (Exception e) {
                        log.error("图片上传失败: {}", imageName, e);
                    }
                }
                
                log.info("图片上传完成: 成功 {}/{}", successCount, imageNames.size());
                logInfo(taskId, "图片上传完成", String.format("成功 %d/%d", successCount, imageNames.size()));
            }
            
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

    /**
     * 语义增强处理
     */
    private String performSemanticEnrichment(String markdown, Map<String, String> imageUrls, Boolean enableVlm, DifyDatasetDetail dataset) {
        boolean enableHeaderProcessing = "hierarchical_model".equalsIgnoreCase(dataset.getDocForm());
        return semanticTextProcessor.enrichMarkdown(markdown, imageUrls, Boolean.TRUE.equals(enableVlm), enableHeaderProcessing);
    }



    /**
     * 查询图片真实 URL
     */
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

    /**
     * 构建 Dify 请求体
     * AUTO 模式：根据 Dataset 的 docForm 自动匹配规则
     * CUSTOM 模式：使用用户自定义规则
     * 
     * @param request 入库请求
     * @param markdown Markdown 内容
     * @param dataset Dataset 详情
     * @return Dify 请求体
     */
    private DifyCreateDocumentRequest buildDifyRequest(IngestRequest request, String markdown, DifyDatasetDetail dataset) {
        boolean isAutoMode = request.getChunkingMode() == null || 
                             request.getChunkingMode().isEmpty() || 
                             "AUTO".equalsIgnoreCase(request.getChunkingMode());
        
        String docForm = dataset.getDocForm();
        String indexingTechnique = dataset.getIndexingTechnique();
        
        // 如果 docForm 为 null，使用默认配置
        if (docForm == null) {
            docForm = appProperties.getDefaultConfig().getDocForm();
        }
        
        DifyCreateDocumentRequest.ProcessRule processRule;
        
        if (isAutoMode) {
            // AUTO 模式：根据 Dataset 类型自动匹配规则
            if ("hierarchical_model".equalsIgnoreCase(docForm)) {
                // 父子结构模型
                AppProperties.HierarchicalModelConfig config = appProperties.getProcessRule().getHierarchicalModel();
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
                                        .separator(config.getSeparator())
                                        .maxTokens(config.getMaxTokens())
                                        .chunkOverlap(config.getChunkOverlap())
                                        .build())
                                .parentMode(config.getParentMode())
                                .subchunkSegmentation(DifyCreateDocumentRequest.Segmentation.builder()
                                        .separator(config.getSubSeparator())
                                        .maxTokens(config.getSubMaxTokens())
                                        .chunkOverlap(config.getChunkOverlap())
                                        .build())
                                .build())
                        .build();
            } else if ("qa_model".equalsIgnoreCase(docForm)) {
                // Q&A 模型
                processRule = DifyCreateDocumentRequest.ProcessRule.builder()
                        .mode("automatic")
                        .build();
            } else {
                // 文本模型（默认）
                AppProperties.TextModelConfig config = appProperties.getProcessRule().getTextModel();
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
                                        .separator(config.getSeparator())
                                        .maxTokens(config.getMaxTokens())
                                        .chunkOverlap(config.getChunkOverlap())
                                        .build())
                                .build())
                        .build();
            }
        } else {
            // CUSTOM 模式：使用用户自定义规则
            if ("hierarchical_model".equalsIgnoreCase(docForm)) {
                // 父子结构模型
                AppProperties.HierarchicalModelConfig defaultConfig = appProperties.getProcessRule().getHierarchicalModel();
                Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : defaultConfig.getMaxTokens();
                Integer subMaxTokens = request.getSubMaxTokens() != null ? request.getSubMaxTokens() : defaultConfig.getSubMaxTokens();
                Integer chunkOverlap = request.getChunkOverlap() != null ? request.getChunkOverlap() : defaultConfig.getChunkOverlap();
                String separator = request.getSeparator() != null ? request.getSeparator() : defaultConfig.getSeparator();
                
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
                                        .separator(separator)
                                        .maxTokens(maxTokens)
                                        .chunkOverlap(chunkOverlap)
                                        .build())
                                .parentMode(defaultConfig.getParentMode())
                                .subchunkSegmentation(DifyCreateDocumentRequest.Segmentation.builder()
                                        .separator(defaultConfig.getSubSeparator())
                                        .maxTokens(subMaxTokens)
                                        .chunkOverlap(chunkOverlap)
                                        .build())
                                .build())
                        .build();
            } else {
                // 文本模型
                AppProperties.TextModelConfig defaultConfig = appProperties.getProcessRule().getTextModel();
                Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : defaultConfig.getMaxTokens();
                Integer chunkOverlap = request.getChunkOverlap() != null ? request.getChunkOverlap() : defaultConfig.getChunkOverlap();
                String separator = request.getSeparator() != null ? request.getSeparator() : defaultConfig.getSeparator();
                
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
        }
        
        return DifyCreateDocumentRequest.builder()
                .name(request.getFileName())
                .text(markdown)
                .indexingTechnique(indexingTechnique != null ? indexingTechnique : appProperties.getDefaultConfig().getIndexingTechnique())
                .docForm(docForm)
                .processRule(processRule)
                .build();
    }

    /**
     * 保存 Markdown 到本地 temp 目录（调试用）
     */
    private void saveMdToTempIfEnabled(String mdContent, String fileName, UUID taskId) {
        if (appProperties.getDebug() == null || !Boolean.TRUE.equals(appProperties.getDebug().getSaveMd())) {
            return;
        }
        
        if (mdContent == null || mdContent.isEmpty()) {
            log.warn("Markdown 内容为空，跳过保存");
            return;
        }
        
        try {
            String tempDir = System.getProperty("tmp"); // todo-hx 改回去
            String mdFileName = fileName.replaceAll("\\.[^.]+$", "") + ".md";
            File mdFile = new File(tempDir, mdFileName);
            
            Files.writeString(mdFile.toPath(), mdContent);
            log.info("Markdown 已保存到: {}", mdFile.getAbsolutePath());
            logInfo(taskId, "Markdown 已保存", mdFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("保存 Markdown 失败", e);
            logWarn(taskId, "保存 Markdown 失败", e.getMessage());
        }
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

    /**
     * 记录 INFO 级别日志
     */
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

    /**
     * 记录 WARN 级别日志
     */
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

    /**
     * 记录 ERROR 级别日志
     */
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
