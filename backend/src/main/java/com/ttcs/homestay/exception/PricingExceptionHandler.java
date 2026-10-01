package com.ttcs.homestay.exception;

import com.ttcs.homestay.controller.pricing.PricingController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** S2-02 Lát 2: đổi lỗi của API tính giá thành thông báo tiếng Việt. */
@RestControllerAdvice(assignableTypes = PricingController.class)
public class PricingExceptionHandler {

    @ExceptionHandler(RoomTypeNotFoundException.class)
    public ResponseEntity<ApiError> handleRoomTypeNotFound(RoomTypeNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(InvalidPriceQuoteException.class)
    public ResponseEntity<ApiError> handleInvalid(InvalidPriceQuoteException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(exception.getMessage()));
    }

    /** Thiếu tham số, hoặc ngày không đúng dạng yyyy-MM-dd. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadParameter(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("Cần roomTypeId, checkIn và checkOut (ngày dạng yyyy-MM-dd)"));
    }
}