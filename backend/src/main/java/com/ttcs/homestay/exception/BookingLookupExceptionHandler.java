package com.ttcs.homestay.exception;

import com.ttcs.homestay.controller.booking.PublicBookingLookupController;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** S2-08: đổi lỗi tra cứu booking thành thông báo tiếng Việt cho khách. */
@RestControllerAdvice(assignableTypes = PublicBookingLookupController.class)
public class BookingLookupExceptionHandler {

    @ExceptionHandler(BookingLookupNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(BookingLookupNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiError.of(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("Vui lòng nhập mã booking và email"));
    }
}