package com.yixing.common;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason() == null ? "请求被拒绝" : exception.getReason()));
    }
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> business(BusinessException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "数据重复、关联不存在或字段超出长度，请检查输入"));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String, String>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "请求格式不正确或上传文件超过 5MB"));
    }
    @ExceptionHandler({java.io.IOException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> file(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "文件内容不正确、字段类型不匹配或无法读取，请检查输入"));
    }
}
