package com.vbgone.controller;

import com.vbgone.ai.ProviderUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps AI-provider failures to a clear, non-fatal JSON error the frontend can
 * surface. A misconfigured or unavailable provider must never crash the app —
 * it returns HTTP 422 with {@code { "error": "<message>" }}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleProviderUnavailable(ProviderUnavailableException ex) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", ex.getMessage()));
    }
}
