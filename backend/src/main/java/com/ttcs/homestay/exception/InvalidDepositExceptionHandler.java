
package com.ttcs.homestay.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InvalidDepositExceptionHandler {

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ApiError> handleDuplicatePayment(DuplicatePaymentException ex) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body(ApiError.of(ex.getMessage()));
    }

    @ExceptionHandler(InvalidDepositException.class)
    public ResponseEntity<ApiError> handleInvalidDeposit(
            InvalidDepositException ex) {

        return ResponseEntity
                .badRequest()
                .body(ApiError.of(ex.getMessage()));
    }
}
