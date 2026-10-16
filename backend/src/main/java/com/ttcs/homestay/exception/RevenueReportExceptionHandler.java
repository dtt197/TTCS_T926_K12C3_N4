package com.ttcs.homestay.exception;

import com.ttcs.homestay.controller.report.RevenueReportController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = RevenueReportController.class)
public class RevenueReportExceptionHandler {

    @ExceptionHandler(InvalidRevenueRangeException.class)
    public ResponseEntity<ApiError> handleInvalidRange(InvalidRevenueRangeException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("Vui lòng chọn ngày bắt đầu và ngày kết thúc."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleBadDate(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("Ngày phải có dạng YYYY-MM-DD."));
    }
}