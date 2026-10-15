package com.ttcs.homestay.controller.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import com.ttcs.homestay.service.NotificationQueryService;
import com.ttcs.homestay.dto.notification.NotificationUnreadCount;
import com.ttcs.homestay.exception.NotificationExceptionHandler;
import com.ttcs.homestay.exception.NotificationNotFoundException;

class NotificationControllerTest {

    @Test
    void usesJwtSubjectAsRecipientAndAcceptsNoRecipientParameter() {
        NotificationQueryService service = mock(NotificationQueryService.class);
        NotificationController controller = new NotificationController(service);
        Jwt jwt = jwt("17", "OWNER");

        controller.list(jwt, 3, 25);

        verify(service).findInbox(org.mockito.ArgumentMatchers.eq(jwt), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq(25));
        verifyNoMoreInteractions(service);
    }

    @Test
    void allowsOnlyOwnerAndReceptionistRoles() {
        assertThat(NotificationController.ALLOWED_ROLES).containsExactly("OWNER", "RECEPTIONIST");
        assertThat(NotificationController.ALLOWED_ROLES).doesNotContain("HOUSEKEEPING", "ADMIN");
    }

    @Test
    void unreadCountUsesAuthenticatedJwtAndReturnsCountDto() {
        NotificationQueryService service = mock(NotificationQueryService.class);
        NotificationController controller = new NotificationController(service);
        Jwt jwt = jwt("17", "OWNER");
        when(service.unreadCount(jwt)).thenReturn(new NotificationUnreadCount(4));

        NotificationUnreadCount result = controller.unreadCount(jwt);

        assertThat(result.unreadCount()).isEqualTo(4);
        verify(service).unreadCount(jwt);
        verifyNoMoreInteractions(service);
    }

    @Test
    void markReadDelegatesAuthenticatedJwtAndNotificationId() {
        NotificationQueryService service = mock(NotificationQueryService.class);
        NotificationController controller = new NotificationController(service);
        Jwt jwt = jwt("17", "RECEPTIONIST");

        controller.markRead(jwt, 81L);

        verify(service).markRead(jwt, 81L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void serviceRejectsUnsupportedRoleAndMalformedJwtSubject() {
        NotificationQueryService service = new NotificationQueryService(mock(
                com.ttcs.homestay.repository.NotificationRepository.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.unreadCount(jwt("17", "HOUSEKEEPING")))
                .isInstanceOf(AccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.unreadCount(jwt("bad-id", "OWNER")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void controllerRejectsUnsupportedRoleAndMalformedJwtBeforeCallingService() {
        NotificationQueryService service = mock(NotificationQueryService.class);
        NotificationController controller = new NotificationController(service);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.unreadCount(jwt("17", "HOUSEKEEPING")))
                .isInstanceOf(AccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.unreadCount(jwt("bad-id", "OWNER")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoMoreInteractions(service);
    }

    @Test
    void missingOrForeignNotificationMapsToNotFound() {
        var response = new NotificationExceptionHandler()
                .handleNotFound(new NotificationNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    private static Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("role", role)
                .build();
    }
}
