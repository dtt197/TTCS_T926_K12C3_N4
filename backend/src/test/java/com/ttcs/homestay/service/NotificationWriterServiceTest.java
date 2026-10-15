package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import com.ttcs.homestay.entity.MaintenanceRequestEntity;
import com.ttcs.homestay.entity.NotificationEntity;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatusHistory;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationWriterServiceTest {

    @Mock
    private NotificationRecipientService recipientService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationEventService notificationEventService;

    private NotificationWriterService writer;

    @BeforeEach
    void setUp() {
        writer = new NotificationWriterService(recipientService, notificationRepository, notificationEventService);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void roomCleanedMapsPersistedHistoryAndRecipients() {
        RoomStatusHistory history = mock(RoomStatusHistory.class);
        Room room = mock(Room.class);
        User actor = mock(User.class);
        User recipient = userWithId(2L);
        when(history.getId()).thenReturn(41L);
        when(history.getRoom()).thenReturn(room);
        when(recipientService.findRecipients()).thenReturn(List.of(recipient));

        writer.writeRoomCleaned(history, actor);

        NotificationEntity notification = captureSingleSave();
        assertThat(notification.getSourceType()).isEqualTo("ROOM_STATUS_HISTORY");
        assertThat(notification.getSourceId()).isEqualTo(41L);
        assertThat(notification.getEventType()).isEqualTo("ROOM_CLEANED");
        assertThat(notification.getRecipient()).isSameAs(recipient);
        assertThat(notification.getActor()).isSameAs(actor);
        assertThat(notification.getRoom()).isSameAs(room);
        assertThat(notification.getRoomStatusHistory()).isSameAs(history);
        assertThat(notification.getMaintenanceRequest()).isNull();
        assertThat(notification.getCreatedAt()).isBeforeOrEqualTo(OffsetDateTime.now());
        assertThat(notification.getReadAt()).isNull();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        verify(notificationEventService, never()).publish(any());
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(notificationEventService).publish(List.of(2L));
    }

    @Test
    void maintenanceReportedMapsPersistedRequestReporterAndRecipients() {
        MaintenanceRequestEntity request = mock(MaintenanceRequestEntity.class);
        Room room = mock(Room.class);
        User reporter = mock(User.class);
        User recipient = userWithId(3L);
        when(request.getId()).thenReturn(52L);
        when(request.getRoom()).thenReturn(room);
        when(request.getReportedBy()).thenReturn(reporter);
        when(recipientService.findRecipients()).thenReturn(List.of(recipient));

        writer.writeMaintenanceReported(request);

        NotificationEntity notification = captureSingleSave();
        assertThat(notification.getSourceType()).isEqualTo("MAINTENANCE_REQUEST");
        assertThat(notification.getSourceId()).isEqualTo(52L);
        assertThat(notification.getEventType()).isEqualTo("MAINTENANCE_REPORTED");
        assertThat(notification.getRecipient()).isSameAs(recipient);
        assertThat(notification.getActor()).isSameAs(reporter);
        assertThat(notification.getRoom()).isSameAs(room);
        assertThat(notification.getRoomStatusHistory()).isNull();
        assertThat(notification.getMaintenanceRequest()).isSameAs(request);
        assertThat(notification.getCreatedAt()).isBeforeOrEqualTo(OffsetDateTime.now());
        assertThat(notification.getReadAt()).isNull();
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(notificationEventService).publish(List.of(3L));
    }

    @Test
    void noRecipientsWritesNothing() {
        RoomStatusHistory history = mock(RoomStatusHistory.class);
        when(history.getId()).thenReturn(7L);
        when(recipientService.findRecipients()).thenReturn(List.of());

        writer.writeRoomCleaned(history, mock(User.class));

        verify(notificationRepository, never()).save(any(NotificationEntity.class));
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void rolledBackTransactionDoesNotPublish() {
        RoomStatusHistory history = mock(RoomStatusHistory.class);
        User recipient = userWithId(9L);
        when(history.getId()).thenReturn(71L);
        when(recipientService.findRecipients()).thenReturn(List.of(recipient));

        writer.writeRoomCleaned(history, mock(User.class));
        TransactionSynchronization synchronization = TransactionSynchronizationManager
                .getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(notificationEventService, never()).publish(any());
    }

    @Test
    void ssePublishFailureAfterCommitDoesNotEscapeCallback() {
        RoomStatusHistory history = mock(RoomStatusHistory.class);
        User recipient = userWithId(11L);
        when(history.getId()).thenReturn(72L);
        when(recipientService.findRecipients()).thenReturn(List.of(recipient));
        org.mockito.Mockito.doThrow(new RuntimeException("emitter failed"))
                .when(notificationEventService).publish(List.of(11L));

        writer.writeRoomCleaned(history, mock(User.class));
        org.assertj.core.api.Assertions.assertThatCode(
                () -> TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit())
                .doesNotThrowAnyException();
    }

    @Test
    void duplicateRecipientIdWritesOnlyOneNotification() {
        RoomStatusHistory history = mock(RoomStatusHistory.class);
        User first = userWithId(4L);
        User duplicate = userWithId(4L);
        when(history.getId()).thenReturn(8L);
        when(recipientService.findRecipients()).thenReturn(List.of(first, duplicate));

        writer.writeRoomCleaned(history, mock(User.class));

        NotificationEntity notification = captureSingleSave();
        assertThat(notification.getRecipient()).isSameAs(first);
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(notificationEventService).publish(List.of(4L));
    }

    @Test
    void nullSourceIdIsRejectedBeforeRecipientLookupOrWrite() {
        RoomStatusHistory history = mock(RoomStatusHistory.class);
        when(history.getId()).thenReturn(null);

        assertThatThrownBy(() -> writer.writeRoomCleaned(history, mock(User.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already be saved");

        verify(recipientService, never()).findRecipients();
        verify(notificationRepository, never()).save(any(NotificationEntity.class));
    }

    @Test
    void missingTransactionIsRejected() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        RoomStatusHistory history = mock(RoomStatusHistory.class);
        when(history.getId()).thenReturn(9L);

        assertThatThrownBy(() -> writer.writeRoomCleaned(history, mock(User.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active writable caller transaction");

        verify(recipientService, never()).findRecipients();
        verify(notificationRepository, never()).save(any(NotificationEntity.class));
    }

    @Test
    void repositoryFailurePropagates() {
        RoomStatusHistory history = mock(RoomStatusHistory.class);
        RuntimeException failure = new RuntimeException("unique constraint failure");
        when(history.getId()).thenReturn(10L);
        User recipient = userWithId(5L);
        when(recipientService.findRecipients()).thenReturn(List.of(recipient));
        when(notificationRepository.save(any(NotificationEntity.class))).thenThrow(failure);

        assertThatThrownBy(() -> writer.writeRoomCleaned(history, mock(User.class)))
                .isSameAs(failure);
    }

    private User userWithId(Long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private NotificationEntity captureSingleSave() {
        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }
}
