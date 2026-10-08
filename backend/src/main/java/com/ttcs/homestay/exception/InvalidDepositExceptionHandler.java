
package com.ttcs.homestay.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InvalidDepositExceptionHandler {

    @ExceptionHandler(InvalidDepositException.class)
    public ResponseEntity<String> handleInvalidDeposit(
            InvalidDepositException ex) {

        return ResponseEntity
                .badRequest()
                .body(ex.getMessage());
    }
}
