package com.example.ingest.exception;

import com.example.ingest.model.IngestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MineruException.class)
    public ResponseEntity<IngestResponse> handleMineruException(MineruException e) {
        log.error("MinerU 异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(IngestResponse.builder()
                        .success(false)
                        .fileIds(Collections.emptyList())
                        .build());
    }

    @ExceptionHandler(DifyException.class)
    public ResponseEntity<IngestResponse> handleDifyException(DifyException e) {
        log.error("Dify 异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(IngestResponse.builder()
                        .success(false)
                        .fileIds(Collections.emptyList())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<IngestResponse> handleException(Exception e) {
        log.error("未知异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(IngestResponse.builder()
                        .success(false)
                        .fileIds(Collections.emptyList())
                        .build());
    }
}
