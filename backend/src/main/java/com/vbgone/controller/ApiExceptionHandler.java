package com.vbgone.controller;

import com.vbgone.ai.ProviderUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps expected service-layer failures to a clear, non-fatal JSON error the frontend can
 * surface as {@code { "error": "<message>" }}, so none of them crash the app as an opaque 500:
 * <ul>
 *   <li>a misconfigured/unavailable AI provider → 422 Unprocessable Entity;</li>
 *   <li>an invalid argument (bad input) → 400 Bad Request;</li>
 *   <li>a wrong-order wizard step, e.g. "Interface must be generated before building"
 *       ({@link IllegalStateException}) → 409 Conflict.</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleProviderUnavailable(ProviderUnavailableException ex) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
