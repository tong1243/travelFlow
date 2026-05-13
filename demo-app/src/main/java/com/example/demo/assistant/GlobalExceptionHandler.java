package com.example.demo.assistant;

import com.example.demo.assistant.dto.ErrorResponse;
import com.example.demo.rag.RagException;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AssistantException.class)
    public ResponseEntity<ErrorResponse> handleAssistantException(AssistantException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(localizeMessage(ex.getMessage()), Instant.now()));
    }

    @ExceptionHandler(RagException.class)
    public ResponseEntity<ErrorResponse> handleRagException(RagException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(localizeMessage(ex.getMessage()), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "请求参数不合法。" : error.getDefaultMessage())
                .orElse("请求参数不合法。");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(localizeMessage(message), Instant.now()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse("请求方法不支持。", Instant.now()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("请求资源不存在。", Instant.now()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        String reason = ex.getReason() == null || ex.getReason().isBlank() ? "请求处理失败。" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(localizeMessage(reason), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(localizeMessage(ex.getMessage()), Instant.now()));
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<String> handleMessageNotWritable(HttpMessageNotWritableException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body("响应写出失败，请稍后重试。");
    }

    private String localizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "请求失败，请稍后重试。";
        }

        String lowered = message.toLowerCase(Locale.ROOT);
        if (lowered.contains("unauthorized")) {
            return "未登录或登录已过期，请重新登录。";
        }
        if (lowered.contains("forbidden")) {
            return "当前账号无权限访问该资源。";
        }
        if (lowered.contains("method not allowed")) {
            return "请求方法不支持。";
        }
        if (lowered.contains("resource not found")) {
            return "请求资源不存在。";
        }
        if (lowered.contains("invalid request parameters")) {
            return "请求参数不合法。";
        }
        if (lowered.contains("internal server error")) {
            return "服务器内部错误，请稍后重试。";
        }
        if (lowered.contains("communications link failure") || lowered.contains("connection refused")) {
            return "数据库连接失败，请检查数据库服务是否启动。";
        }
        if (lowered.contains("access denied for user")) {
            return "数据库账号或密码错误，请检查数据源配置。";
        }
        if (lowered.contains("doesn't exist")) {
            return "数据表不存在，请先初始化数据库。";
        }
        if (lowered.contains("data too long for column 'answer_text'") || lowered.contains("data truncation")) {
            return "行程内容过长，正在自动升级存储字段。请重启后端后重试。";
        }
        if (lowered.contains("could not execute statement")) {
            return "数据库写入失败，请稍后重试。";
        }

        return message;
    }
}
