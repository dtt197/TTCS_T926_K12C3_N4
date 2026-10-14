package com.ttcs.homestay.controller.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ttcs.homestay.service.NotificationEventService;

class NotificationStreamControllerTest {

    @Test
    void connectsUsingAuthenticatedSubjectForAllowedRole() {
        NotificationEventService events = mock(NotificationEventService.class);
        SseEmitter emitter = new SseEmitter();
        org.mockito.Mockito.when(events.connect(14L)).thenReturn(emitter);
        NotificationStreamController controller = new NotificationStreamController(events);

        assertThat(controller.stream(jwt("14", "OWNER"))).isSameAs(emitter);
        verify(events).connect(14L);
    }

    @Test
    void rejectsOtherRolesAndMalformedSubjectsBeforeConnecting() {
        NotificationEventService events = mock(NotificationEventService.class);
        NotificationStreamController controller = new NotificationStreamController(events);

        assertThatThrownBy(() -> controller.stream(jwt("14", "ADMIN")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.stream(jwt("bad-id", "RECEPTIONIST")))
                .isInstanceOf(AccessDeniedException.class);
        verify(events, org.mockito.Mockito.never()).connect(org.mockito.ArgumentMatchers.any());
    }

    private Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("header.payload.signature")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2027-01-01T00:00:00Z"))
                .claim("role", role)
                .audience(List.of("homestay-api"))
                .build();
    }
}
