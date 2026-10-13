package com.x.cloudnext.server.web;

import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class CloudNextExceptionHandler {

    private final String maxFileSize;

    public CloudNextExceptionHandler(
            @Value("${spring.servlet.multipart.max-file-size:100MB}") String maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Collections.singletonMap("message", "上传文件大小不能超过 " + maxFileSize));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getStatus().getReasonPhrase();
        }
        Map<String,String> body=new java.util.LinkedHashMap<>();
        body.put("message",message);
        if(exception instanceof com.x.cloudnext.server.form.FormSsoException)
            body.put("code",((com.x.cloudnext.server.form.FormSsoException)exception).getCode());
        return ResponseEntity.status(exception.getStatus()).body(body);
    }
}
