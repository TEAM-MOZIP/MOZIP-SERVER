package com.mozip.server.global.exception;

import com.mozip.server.global.dto.ErrorResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        return buildBindingErrorResponse(e.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException e) {
        return buildBindingErrorResponse(e.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        List<ErrorResponse.FieldError> errors = List.of(
                new ErrorResponse.FieldError(e.getName(), "요청 값의 타입이 올바르지 않습니다.")
        );
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode.name(), errorCode.getDefaultMessage(), errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode.name(), errorCode.getDefaultMessage()));
    }

    private ResponseEntity<ErrorResponse> buildBindingErrorResponse(List<org.springframework.validation.FieldError> fieldErrors) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        List<ErrorResponse.FieldError> errors = fieldErrors.stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode.name(), errorCode.getDefaultMessage(), errors));
    }
}