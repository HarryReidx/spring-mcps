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
        response.put("errorMsg", "Dify API 异常: " + e.getMessage());
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
