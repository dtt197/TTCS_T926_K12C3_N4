package com.ttcs.homestay.exception;

import com.ttcs.homestay.controller.maintenance.MaintenanceWorkflowController;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = MaintenanceWorkflowController.class)
public class MaintenanceWorkflowExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "Yêu cầu không hợp lệ" : e.getReason()));
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> forbidden(AccessDeniedException e) {
        return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Tên đơn vị, số điện thoại và ghi chú không hợp lệ"));
    }
}
