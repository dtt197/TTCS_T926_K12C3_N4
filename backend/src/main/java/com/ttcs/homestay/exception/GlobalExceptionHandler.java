package com.ttcs.homestay.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
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
}