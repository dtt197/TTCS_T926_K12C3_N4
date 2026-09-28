package com.ttcs.homestay.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ttcs.homestay.controller.AuthController;
import com.ttcs.homestay.controller.PasswordResetController; 
import java.util.Map;

@RestControllerAdvice(assignableTypes = {AuthController.class, PasswordResetController.class}) 
public class GlobalExceptionHandler {

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
				"code", "INVALID_CREDENTIALS",
				"message", exception.getMessage()
		));
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<Map<String, String>> handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
				"code", "INVALID_REFRESH_TOKEN",
				"message", exception.getMessage()
		));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidation() {
		return ResponseEntity.badRequest().body(Map.of(
				"code", "INVALID_REQUEST",
				"message", "Thông tin đăng nhập không hợp lệ"
		));
	}

	@ExceptionHandler(InvalidResetTokenException.class)
	public ResponseEntity<Map<String, String>> handleInvalidResetToken(InvalidResetTokenException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
				"code", "INVALID_RESET_TOKEN",
				"message", exception.getMessage()
		));
	}

	@ExceptionHandler(TooManyResetRequestsException.class)
	public ResponseEntity<Map<String, String>> handleTooManyResetRequests(TooManyResetRequestsException exception) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
				"code", "TOO_MANY_RESET_REQUESTS",
				"message", exception.getMessage()
		));
	}
}