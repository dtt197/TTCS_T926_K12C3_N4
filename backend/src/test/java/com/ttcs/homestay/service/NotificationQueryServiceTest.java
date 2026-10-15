package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import com.ttcs.homestay.dto.notification.NotificationPage;
import com.ttcs.homestay.dto.notification.NotificationUnreadCount;
import com.ttcs.homestay.entity.NotificationEntity;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.NotificationRepository;
import com.ttcs.homestay.exception.NotificationNotFoundException;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    void filtersByAuthenticatedRecipientAndMapsOnlyInboxFields() {
        NotificationEntity entity = notification(8L, 42L);
        when(notificationRepository.findByRecipient_Id(eq(17L), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

        NotificationPage page = new NotificationQueryService(notificationRepository)
                .findInbox(jwt("17", "OWNER"), 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).id()).isEqualTo(8L);
        assertThat(page.content().get(0).roomId()).isEqualTo(42L);
        assertThat(page.content().get(0).actorUserId()).isEqualTo(9L);
        assertThat(page.content().get(0).eventType()).isEqualTo("ROOM_CLEANED");
        assertThat(page.content().get(0).sourceType()).isEqualTo("ROOM_STATUS_HISTORY");
        assertThat(page.content().get(0).sourceId()).isEqualTo(33L);
        assertThat(page.content().get(0).createdAt()).isNotNull();
        assertThat(page.content().get(0).readAt()).isNull();
        verify(notificationRepository).findByRecipient_Id(eq(17L), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void clampsPageSizeAndUsesCreatedAtThenIdDescending() {
        when(notificationRepository.findByRecipient_Id(eq(17L), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 100), 0));

        new NotificationQueryService(notificationRepository).findInbox(jwt("17", "RECEPTIONIST"), 2, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipient_Id(eq(17L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    void normalizesNegativePageAndDefaultsNonPositiveSize() {
        when(notificationRepository.findByRecipient_Id(eq(17L), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        new NotificationQueryService(notificationRepository).findInbox(jwt("17", "OWNER"), -3, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipient_Id(eq(17L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void countsUnreadOnlyForAuthenticatedRecipient() {
        when(notificationRepository.countByRecipient_IdAndReadAtIsNull(17L)).thenReturn(3L);

        NotificationUnreadCount count = new NotificationQueryService(notificationRepository)
                .unreadCount(jwt("17", "OWNER"));

        assertThat(count.unreadCount()).isEqualTo(3L);
        verify(notificationRepository).countByRecipient_IdAndReadAtIsNull(17L);
        verifyNoMoreInteractions(notificationRepository);
    }

    @Test
    void markReadSetsTimestampForUnreadNotification() {
        NotificationEntity entity = new NotificationEntity();
        when(notificationRepository.findByIdAndRecipient_Id(8L, 17L)).thenReturn(Optional.of(entity));

        new NotificationQueryService(notificationRepository).markRead(jwt("17", "RECEPTIONIST"), 8L);

        assertThat(entity.getReadAt()).isNotNull();
        verify(notificationRepository).findByIdAndRecipient_Id(8L, 17L);
    }

    @Test
    void markReadLeavesExistingTimestampUnchanged() {
        OffsetDateTime original = OffsetDateTime.parse("2026-10-14T10:00:00+07:00");
        NotificationEntity entity = new NotificationEntity();
        entity.setReadAt(original);
        when(notificationRepository.findByIdAndRecipient_Id(8L, 17L)).thenReturn(Optional.of(entity));

        new NotificationQueryService(notificationRepository).markRead(jwt("17", "OWNER"), 8L);

        assertThat(entity.getReadAt()).isSameAs(original);
    }

    @Test
    void markReadDoesNotFindOrModifyNotificationOwnedByAnotherUser() {
        when(notificationRepository.findByIdAndRecipient_Id(8L, 17L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new NotificationQueryService(notificationRepository)
                .markRead(jwt("17", "OWNER"), 8L))
                .isInstanceOf(NotificationNotFoundException.class);
        verify(notificationRepository).findByIdAndRecipient_Id(8L, 17L);
        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidRoleAndMalformedJwtSubject() {
        NotificationQueryService service = new NotificationQueryService(notificationRepository);

        assertThatThrownBy(() -> service.unreadCount(jwt("17", "HOUSEKEEPING")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.unreadCount(jwt("bad-id", "OWNER")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(notificationRepository);
    }

    private static Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim("role", role)
                .build();
    }

    private static NotificationEntity notification(Long id, Long roomId) {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(id);
        entity.setEventType("ROOM_CLEANED");
        entity.setSourceType("ROOM_STATUS_HISTORY");
        entity.setSourceId(33L);
        entity.setCreatedAt(OffsetDateTime.parse("2026-10-14T10:00:00+07:00"));
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        entity.setRoom(room);
        User actor = mock(User.class);
        when(actor.getId()).thenReturn(9L);
        entity.setActor(actor);
        User recipient = mock(User.class);
        entity.setRecipient(recipient);
        return entity;
    }
}
