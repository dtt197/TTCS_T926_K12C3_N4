package com.ttcs.homestay.controller.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ttcs.homestay.dto.user.CreateUserRequest;
import com.ttcs.homestay.dto.user.UpdateUserRequest;
import com.ttcs.homestay.dto.user.UpdateUserStatusRequest;
import com.ttcs.homestay.dto.user.UserResponse;
import com.ttcs.homestay.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> listUsers() {
        return userService.listUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {

        return userService.createUser(
                request,
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                httpRequest.getRemoteAddr()
        );
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {

        return userService.updateStatus(
                id,
                request,
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                httpRequest.getRemoteAddr()
        );
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {

        return userService.updateUser(
                id,
                request,
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                httpRequest.getRemoteAddr()
        );
    }

    @PostMapping("/{id}/resend-temporary-password")
    public UserResponse resendTemporaryPassword(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {

        return userService.resendTemporaryPassword(
                id,
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                httpRequest.getRemoteAddr()
        );
    }
}