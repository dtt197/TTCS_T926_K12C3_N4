package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.dto.booking.BookingSurchargeListResponse;
import com.ttcs.homestay.dto.booking.BookingSurchargeRequest;
import com.ttcs.homestay.exception.ApiError;
import com.ttcs.homestay.service.BookingSurchargeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** S4-02 Lát 1: xem và thêm dòng phụ thu của booking. */
@RestController
@RequestMapping("/api/bookings/{id}/surcharges")
public class BookingSurchargeController {

    private final BookingSurchargeService surchargeService;

    public BookingSurchargeController(BookingSurchargeService surchargeService) {
        this.surchargeService = surchargeService;
    }

    @GetMapping
    public BookingSurchargeListResponse getSurcharges(@PathVariable Long id) {
        return surchargeService.getSurcharges(id);
    }

    @PostMapping
    public ResponseEntity<BookingSurchargeListResponse> addSurcharge(
            @PathVariable Long id,
            @Valid @RequestBody BookingSurchargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(surchargeService.addSurcharge(id, request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(ApiError.of(exception.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Dữ liệu phụ thu không hợp lệ");
        return ResponseEntity.badRequest().body(ApiError.of(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("Loại phụ thu hoặc số liệu không hợp lệ"));
    }
}