package com.example.difyingest.exception;

import com.example.difyingest.model.DocumentIngestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<DocumentIngestResponse> handleException(Exception e) {
        log.error("处理请求时发生异常: {}", e.getMessage(), e);
        
        DocumentIngestResponse response = DocumentIngestResponse.builder()
                .success(false)
                .message(e.getMessage() != null ? e.getMessage() : "处理失败")
                .fileIds(Collections.emptyList())
                .stats(DocumentIngestResponse.Stats.builder()
                        .chunkCount(0)
                        .imageCount(0)
                        .build())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<DocumentIngestResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("参数错误: {}", e.getMessage());
        
        DocumentIngestResponse response = DocumentIngestResponse.builder()
                .success(false)
                .message("参数错误: " + e.getMessage())
                .fileIds(Collections.emptyList())
                .stats(DocumentIngestResponse.Stats.builder()
                        .chunkCount(0)
                        .imageCount(0)
                        .build())
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
