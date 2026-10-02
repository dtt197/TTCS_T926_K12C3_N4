package com.ttcs.homestay.exception;

import com.ttcs.homestay.controller.booking.PublicBookingController;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** S2-07: đổi lỗi của trang đặt phòng công khai thành thông báo tiếng Việt cho khách. */
@RestControllerAdvice(assignableTypes = PublicBookingController.class)
public class GuestBookingExceptionHandler {

    @ExceptionHandler(InvalidGuestBookingException.class)
    public ResponseEntity<ApiError> handleInvalid(InvalidGuestBookingException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(RoomUnavailableException.class)
    public ResponseEntity<ApiError> handleUnavailable(RoomUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(exception.getMessage()));
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
        return ResponseEntity.badRequest()
                .body(ApiError.of("Dữ liệu không hợp lệ, vui lòng kiểm tra ngày và số khách"));
    }
}