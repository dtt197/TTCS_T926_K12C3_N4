package com.ttcs.homestay.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class BookingRateLimitException extends RuntimeException {

    public BookingRateLimitException(String message) {
        super(message);
    }
}