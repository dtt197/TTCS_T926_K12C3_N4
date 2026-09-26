package com.ttcs.homestay.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ttcs.homestay.controller.UserController;
import com.ttcs.homestay.controller.AccountController;

@RestControllerAdvice(assignableTypes = { UserController.class, AccountController.class })
public class UserAdminExceptionHandler {

	@ExceptionHandler(EmailAlreadyUsedException.class)
	public ResponseEntity<ApiError> handleEmailAlreadyUsed(EmailAlreadyUsedException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(exception.getMessage()));
	}

	@ExceptionHandler(PasswordChangeException.class)
	public ResponseEntity<ApiError> handlePasswordChange(PasswordChangeException exception) {
		return ResponseEntity.badRequest().body(ApiError.of(exception.getMessage()));
	}
	
	@ExceptionHandler(InvalidRoleException.class)
	public ResponseEntity<ApiError> handleInvalidRole(InvalidRoleException exception) {
		return ResponseEntity.badRequest().body(ApiError.of(exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getDefaultMessage())
				.collect(Collectors.joining(", "));
		return ResponseEntity.badRequest().body(ApiError.of(message));
	}
}