package com.ttcs.homestay.exception;

import com.ttcs.homestay.controller.booking.BookingInvoiceController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Returns actionable invoice validation reasons instead of Spring's generic error body. */
@RestControllerAdvice(assignableTypes = BookingInvoiceController.class)
public class BookingInvoiceExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        String reason = exception.getReason();
        String safeMessage = reason == null || reason.isBlank()
                ? "Không thể xuất hóa đơn với dữ liệu hiện tại."
                : reason;
        return ResponseEntity.status(exception.getStatusCode()).body(ApiError.of(safeMessage));
    }
}
