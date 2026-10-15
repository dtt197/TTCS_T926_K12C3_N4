package com.ttcs.homestay.controller.notification;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ttcs.homestay.dto.notification.NotificationPage;
import com.ttcs.homestay.dto.notification.NotificationUnreadCount;
import com.ttcs.homestay.service.NotificationQueryService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public static final String[] ALLOWED_ROLES = {"OWNER", "RECEPTIONIST"};
    private static final Set<String> ALLOWED_ROLE_SET = Set.of(ALLOWED_ROLES);

    private final NotificationQueryService notificationQueryService;

    public NotificationController(NotificationQueryService notificationQueryService) {
        this.notificationQueryService = notificationQueryService;
    }

    @GetMapping
    public NotificationPage list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationQueryService.findInbox(authorizedJwt(jwt), page, size);
    }

    @GetMapping("/unread-count")
    public NotificationUnreadCount unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return notificationQueryService.unreadCount(authorizedJwt(jwt));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        notificationQueryService.markRead(authorizedJwt(jwt), id);
    }

    private Jwt authorizedJwt(Jwt jwt) {
        if (jwt == null || !ALLOWED_ROLE_SET.contains(jwt.getClaimAsString("role"))) {
            throw new AccessDeniedException("Only active owners and receptionists may access notifications");
        }
        try {
            Long.valueOf(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new AccessDeniedException("Invalid authenticated user", exception);
        }
        return jwt;
    }
}
