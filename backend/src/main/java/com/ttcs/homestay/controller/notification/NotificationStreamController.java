package com.ttcs.homestay.controller.notification;

import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ttcs.homestay.service.NotificationEventService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationStreamController {

    private static final Set<String> ALLOWED_ROLES = Set.of("OWNER", "RECEPTIONIST");
    private final NotificationEventService notificationEventService;

    public NotificationStreamController(NotificationEventService notificationEventService) {
        this.notificationEventService = notificationEventService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null || !ALLOWED_ROLES.contains(jwt.getClaimAsString("role"))) {
            throw new AccessDeniedException("Only active owners and receptionists may access notifications");
        }
        try {
            return notificationEventService.connect(Long.valueOf(jwt.getSubject()));
        } catch (RuntimeException exception) {
            throw new AccessDeniedException("Invalid authenticated user", exception);
        }
    }
}
