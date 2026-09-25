package com.ttcs.homestay.controller;

import com.ttcs.homestay.dto.auth.InternalUserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
public class InternalController {

	@GetMapping("/me")
	public ResponseEntity<InternalUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(new InternalUserResponse(
				Long.valueOf(jwt.getSubject()),
				jwt.getClaimAsString("fullName"),
				jwt.getClaimAsString("email"),
				jwt.getClaimAsString("role")
		));
	}
}