package com.example.test.exception;

import com.example.test.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WalletException.class)
    public ResponseEntity<ApiResponse<Void>> handleWallet(WalletException exception) {
        ErrorCode code = exception.getErrorCode();
        if (code.status().is5xxServerError()) {
            log.error("{} - {}", code, exception.getMessage(), exception);
        } else {
            log.warn("{} - {}", code, exception.getMessage());
        }
        return ResponseEntity.status(code.status())
                .body(ApiResponse.failure(code.name(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(),
                        "One or more fields failed validation", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParameterValidation(ConstraintViolationException exception) {
        // Constraint failures on request parameters / path variables (e.g. @Min/@Max on the
        // statement `size`), raised by @Validated controllers. These are client errors and belong at
        // 400, not the 500 the catch-all would otherwise produce.
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            errors.putIfAbsent(lastNode(violation), violation.getMessage());
        }
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(),
                        "One or more request parameters failed validation", errors));
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformed(Exception exception) {
        log.warn("Malformed request: {}", exception.getMessage());
        return ResponseEntity.status(ErrorCode.MALFORMED_REQUEST.status())
                .body(ApiResponse.failure(ErrorCode.MALFORMED_REQUEST.name(),
                        "The request could not be read"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException exception) {
        log.debug("No resource for {}", exception.getResourcePath());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.status())
                .body(ApiResponse.failure(ErrorCode.RESOURCE_NOT_FOUND.name(), "Resource not found"));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleLockTimeout(PessimisticLockingFailureException exception) {
        log.warn("Lock acquisition failed: {}", exception.getMessage());
        return ResponseEntity.status(ErrorCode.WALLET_LOCKED.status())
                .body(ApiResponse.failure(ErrorCode.WALLET_LOCKED.name(),
                        "The account is busy with another transaction, please retry"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR.name(),
                        "An unexpected error occurred"));
    }
}
