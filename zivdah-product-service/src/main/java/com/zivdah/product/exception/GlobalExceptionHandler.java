package com.zivdah.product.exception;

import com.zivdah.product.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handle explicit status exceptions (e.g. NOT_FOUND). Without this, the
    // generic RuntimeException handler below matches first and forces every
    // status to 400, even when the service explicitly threw a 404/403/etc.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatusException(ResponseStatusException ex) {
        ApiResponse<Object> response = ApiResponse.builder()
                .status("error")
                .message(ex.getReason())
                .statusCode(ex.getStatusCode().value())
                .data(null)
                .build();

        return new ResponseEntity<>(response, ex.getStatusCode());
    }

    // Handle authorization failures (@PreAuthorize, role checks)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException ex) {
        ApiResponse<Object> response = ApiResponse.builder()
                .status("error")
                .message("Access denied")
                .statusCode(HttpStatus.FORBIDDEN.value())
                .data(null)
                .build();

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // Handle validation errors (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ApiResponse<Object> response = ApiResponse.builder()
                .status("error")
                .message(errors)
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .data(null)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // Handle runtime exceptions (custom or general)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException ex) {
        ApiResponse<Object> response = ApiResponse.builder()
                .status("error")
                .message(ex.getMessage())
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .data(null)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // Handle generic exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception ex) {
        ApiResponse<Object> response = ApiResponse.builder()
                .status("error")
                .message("Internal server error")
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .data(null)
                .build();

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
