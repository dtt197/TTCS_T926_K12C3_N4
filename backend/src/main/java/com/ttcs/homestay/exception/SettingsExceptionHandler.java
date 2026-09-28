package com.ttcs.homestay.exception;

import com.ttcs.homestay.controller.OperatingSettingsController;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** S1-09: đổi lỗi của API tham số thành thông báo tiếng Việt kèm mã HTTP phù hợp. */
@RestControllerAdvice(assignableTypes = OperatingSettingsController.class)
public class SettingsExceptionHandler {

    @ExceptionHandler(SettingsNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(SettingsNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(exception.getMessage()));
    }

    /** AC3: mốc huỷ chồng lấn / không giảm dần, giờ nhận – trả phòng không hợp lý. */
    @ExceptionHandler(InvalidSettingsException.class)
    public ResponseEntity<ApiError> handleInvalid(InvalidSettingsException exception) {
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

    /** Ví dụ giờ không đúng định dạng HH:mm, hoặc gõ chữ vào ô số tiền. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("Dữ liệu gửi lên không hợp lệ, vui lòng kiểm tra giờ (HH:mm) và các ô số tiền"));
    }
}