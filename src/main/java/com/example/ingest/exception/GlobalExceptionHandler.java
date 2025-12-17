package com.example.ingest.exception;

import com.example.ingest.model.IngestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 
 * @author HarryReid(黄药师)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("参数校验失败", e);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorMsg", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MineruException.class)
    public ResponseEntity<Map<String, Object>> handleMineruException(MineruException e) {
        log.error("MinerU 异常", e);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorMsg", "MinerU 服务异常: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(DifyException.class)
    public ResponseEntity<Map<String, Object>> handleDifyException(DifyException e) {
        log.error("Dify 异常", e);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        
        // 判断是否为知识库不存在错误
        String errorMsg = e.getMessage();
        if (errorMsg != null && (errorMsg.contains("404") || errorMsg.contains("not found") || errorMsg.contains("不存在"))) {
            response.put("errorMsg", "知识库不存在，请检查 datasetId 是否正确");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        
        response.put("errorMsg", "Dify API 异常: " + errorMsg);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorMsg", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("未知异常", e);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorMsg", "系统异常: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
