package com.ttcs.homestay.exception;

import com.ttcs.homestay.controller.room.RoomTypeController;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** S1-06: đổi lỗi của API loại phòng thành thông báo tiếng Việt kèm mã HTTP phù hợp. */
@RestControllerAdvice(assignableTypes = RoomTypeController.class)
public class RoomTypeExceptionHandler {

        @ExceptionHandler(RoomTypeNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(RoomTypeNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(exception.getMessage()));
    }

    /** S1-08: gắn tiện nghi không tồn tại. */
    @ExceptionHandler(AmenityNotFoundException.class)
    public ResponseEntity<ApiError> handleAmenityNotFound(AmenityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(RoomTypeConflictException.class)
    public ResponseEntity<ApiError> handleConflict(RoomTypeConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(InvalidRoomTypeCapacityException.class)
    public ResponseEntity<ApiError> handleCapacity(InvalidRoomTypeCapacityException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiError.of(message));
    }

    /** Ví dụ gõ chữ vào ô sức chứa. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("Dữ liệu gửi lên không hợp lệ, vui lòng kiểm tra lại các ô số"));
    }

    /** Hai người cùng thêm một mã đúng lúc: database chặn, báo trùng thay vì lỗi 500. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("Mã hoặc tên loại phòng đã tồn tại, vui lòng tải lại danh sách"));
    }

    /** S2-09: Lỗi tải ảnh loại phòng */
    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<ApiError> handleInvalidImage(InvalidImageException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(ImageSizeExceededException.class)
    public ResponseEntity<ApiError> handleImageSizeExceeded(ImageSizeExceededException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(MaxImageCountExceededException.class)
    public ResponseEntity<ApiError> handleMaxImageCount(MaxImageCountExceededException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("Kích thước tệp vượt quá giới hạn tối đa 5MB."));
    }

    @ExceptionHandler(CannotDeleteLastImageException.class)
    public ResponseEntity<ApiError> handleCannotDeleteLastImage(CannotDeleteLastImageException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(RoomTypeImageNotFoundException.class)
    public ResponseEntity<ApiError> handleImageNotFound(RoomTypeImageNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(exception.getMessage()));
    }
}