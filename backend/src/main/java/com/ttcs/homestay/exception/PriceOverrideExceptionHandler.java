package com.ttcs.homestay.exception;

import com.ttcs.homestay.controller.pricing.PriceOverrideController;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** S2-02: đổi lỗi của API giá đè thành thông báo tiếng Việt kèm mã HTTP phù hợp. */
@RestControllerAdvice(assignableTypes = PriceOverrideController.class)
public class PriceOverrideExceptionHandler {

    @ExceptionHandler(PriceOverrideNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(PriceOverrideNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(InvalidPriceOverrideException.class)
    public ResponseEntity<ApiError> handleInvalid(InvalidPriceOverrideException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiError.of(message));
    }

    /** Ví dụ ngày sai định dạng, hoặc gõ chữ / số thập phân vào ô giá. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("Dữ liệu không hợp lệ, vui lòng kiểm tra ngày và giá một đêm (số nguyên VND)"));
    }
}