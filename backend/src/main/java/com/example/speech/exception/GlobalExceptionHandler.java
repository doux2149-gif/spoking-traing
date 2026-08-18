package com.example.speech.exception;

import java.time.Instant;
import java.util.concurrent.TimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request, HttpServletResponse response) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, response);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiError> handleSecurity(SecurityException exception, HttpServletRequest request, HttpServletResponse response) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage(), request, response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request, HttpServletResponse response) {
        return response(HttpStatus.FORBIDDEN, "权限不足，无法访问", request, response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(HttpServletRequest request, HttpServletResponse response) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "文件过大，请上传不超过 60 秒的音频", request, response);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiError> handleTimeout(HttpServletRequest request, HttpServletResponse response) {
        return response(HttpStatus.GATEWAY_TIMEOUT, "识别等待超时，请稍后重试", request, response);
    }

    /**
     * 专门处理 Spring 写响应失败异常(SSE 场景常见)。
     * 当 produces=text/event-stream 时,Spring 找不到 converter 把 JSON 对象写成 SSE 格式,
     * 抛出此异常。此时 response 通常已提交,直接返回 null 不再尝试写入。
     */
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<ApiError> handleNotWritable(HttpMessageNotWritableException exception, HttpServletResponse response) {
        // 这是 SSE 场景下的预期异常,不需要 WARN 级别日志
        LOGGER.debug("HttpMessageNotWritable (expected for SSE): {}", exception.getMessage());
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request, HttpServletResponse response) {
        if (response.isCommitted()) {
            LOGGER.debug("Response already committed, skipping: {}", exception.getMessage());
            return null;
        }
        LOGGER.error("Unhandled API error", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时不可用，请稍后重试", request, response);
    }

    /**
     * 统一构建错误响应。
     * SSE 端点(Accept: text/event-stream)的异常不能通过 ResponseEntity 返回 JSON,
     * 因为 produces=text/event-stream 时 Spring 找不到 converter 转 JSON → 抛二次异常。
     * 检测到 SSE 请求时,直接手动写 JSON 到 response 并返回 null。
     */
    private static ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request, HttpServletResponse servletResponse) {
        if (servletResponse.isCommitted()) {
            LOGGER.debug("Response already committed, skipping: {}", message);
            return null;
        }
        // 检测 SSE 请求:前端发送 Accept: text/event-stream
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            try {
                servletResponse.setStatus(status.value());
                servletResponse.setContentType("application/json;charset=UTF-8");
                servletResponse.getWriter().write("{\"message\":\"" + message.replace("\"", "'") + "\",\"timestamp\":\"" + Instant.now() + "\"}");
                servletResponse.getWriter().flush();
            } catch (Exception e) {
                LOGGER.debug("Failed to write SSE error response", e);
            }
            return null;
        }
        return ResponseEntity.status(status).body(new ApiError(message, Instant.now()));
    }
}
