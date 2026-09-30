package com.ttcs.homestay.exception;

import com.ttcs.homestay.controller.room.AmenityController;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** S1-08: đổi lỗi của API tiện nghi thành thông báo tiếng Việt kèm mã HTTP phù hợp. */
@RestControllerAdvice(assignableTypes = AmenityController.class)
public class AmenityExceptionHandler {

    @ExceptionHandler(AmenityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(AmenityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(AmenityConflictException.class)
    public ResponseEntity<ApiError> handleConflict(AmenityConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiError.of(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("Dữ liệu gửi lên không hợp lệ"));
    }

    /** Hai người cùng thêm một mã đúng lúc, hoặc xoá tiện nghi vừa được gắn: database chặn, báo lại thay vì lỗi 500. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("Mã tiện nghi đã tồn tại hoặc tiện nghi đang được dùng, vui lòng tải lại danh sách"));
    }
}