package com.ttcs.homestay.exception;

import com.ttcs.homestay.controller.booking.PublicBookingController;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** S2-07: đổi lỗi của trang đặt phòng công khai thành thông báo tiếng Việt cho khách. */
@RestControllerAdvice(assignableTypes = PublicBookingController.class)
public class GuestBookingExceptionHandler {

    @ExceptionHandler(RoomTypeNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(RoomTypeNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(exception.getMessage()));
    }

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
        /** S2-06: thiếu tham số tạm tính, hoặc ngày không đúng dạng yyyy-MM-dd. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadParameter(Exception exception) {
        return ResponseEntity.badRequest().body(ApiError.of("Vui lòng chọn loại phòng, ngày nhận và ngày trả phòng"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("Dữ liệu không hợp lệ, vui lòng kiểm tra ngày và số khách"));
    }
}