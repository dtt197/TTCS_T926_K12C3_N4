package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;

import com.ttcs.homestay.dto.IncidentReportRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.entity.MaintenanceRequestEntity;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomStatusHistory;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.RoomStatusConflictException;
import com.ttcs.homestay.repository.MaintenanceRequestRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;
import com.ttcs.homestay.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

class IncidentReportServiceTest {

    private static final Long ROOM_ID = 42L;
    private static final Long REPORTER_ID = 7L;
    private static final String DESCRIPTION = "Leaking sink";
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("0d0ac16f-5d81-40cf-9ecf-3b6b7a2e8e12");

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final MaintenanceRequestRepository maintenanceRequestRepository = mock(MaintenanceRequestRepository.class);
    private final RoomStatusHistoryRepository roomStatusHistoryRepository = mock(RoomStatusHistoryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final NotificationWriterService notificationWriterService = mock(NotificationWriterService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = new SimpleTransactionStatus();

    private IncidentReportService service;
    private Room room;
    private User reporter;

    @BeforeEach
    void setUp() {
        service = new IncidentReportService(
                roomRepository,
                maintenanceRequestRepository,
                roomStatusHistoryRepository,
                userRepository,
                auditLogService,
                notificationWriterService,
                transactionManager);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);

        room = new Room();
        room.setId(ROOM_ID);
        room.setRoomNumber("204");
        room.setFloor(2);
        room.setRoomType("Double");
        room.setActive(true);
        room.setStatus(RoomStatus.TRONG_BAN);

        reporter = mock(User.class);
        when(reporter.getId()).thenReturn(REPORTER_ID);
        when(reporter.getFullName()).thenReturn("Housekeeper");
        when(reporter.getEmail()).thenReturn("housekeeper@example.test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NHE", "NGHIEM_TRONG"})
    void createsRequestAndMovesDirtyRoomToMaintenance(String severity) {
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));
        when(userRepository.findWithRoleById(REPORTER_ID)).thenReturn(Optional.of(reporter));
        when(roomRepository.save(room)).thenReturn(room);
        when(maintenanceRequestRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(maintenanceRequestRepository.saveAndFlush(any(MaintenanceRequestEntity.class)))
                .thenAnswer(invocation -> {
                    MaintenanceRequestEntity saved = invocation.getArgument(0);
                    saved.setId(101L);
                    return saved;
                });

        RoomResponse response = service.report(
                ROOM_ID, request(severity, DESCRIPTION), IDEMPOTENCY_KEY,
                REPORTER_ID, "actor@example.test", "127.0.0.1");

        assertThat(response.status()).isEqualTo(RoomStatus.BAO_TRI);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.BAO_TRI);
        assertThat(room.getMaintenanceReason()).isEqualTo(DESCRIPTION);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(MaintenanceRequestEntity.class);
        verify(maintenanceRequestRepository).saveAndFlush(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getSeverity()).isEqualTo(severity);
        assertThat(requestCaptor.getValue().getDescription()).isEqualTo(DESCRIPTION);
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo("CHO_TIEP_NHAN");
        assertThat(requestCaptor.getValue().getReportedBy()).isSameAs(reporter);
        assertThat(requestCaptor.getValue().getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(requestCaptor.getValue().getId()).isEqualTo(101L);
        verify(notificationWriterService).writeMaintenanceReported(requestCaptor.getValue());

        var historyCaptor = org.mockito.ArgumentCaptor.forClass(RoomStatusHistory.class);
        verify(roomStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getRoom()).isSameAs(room);
        assertThat(historyCaptor.getValue().getPreviousStatus()).isEqualTo(RoomStatus.TRONG_BAN);
        assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo(RoomStatus.BAO_TRI);
        assertThat(historyCaptor.getValue().getChangedAt()).isNotNull();
        assertThat(historyCaptor.getValue().getChangedBy()).isEqualTo("Housekeeper");
        verify(userRepository).findWithRoleById(REPORTER_ID);
        verify(auditLogService).recordSensitiveAction(
                REPORTER_ID, "actor@example.test", null, "Phòng #" + ROOM_ID,
                "ROOM_INCIDENT_REPORTED", "127.0.0.1");
    }

    @Test
    void replaysSameKeyReporterAndPayloadWithoutCreatingAnotherRequest() {
        User existingReporter = reporter(REPORTER_ID, "Housekeeper", "housekeeper@example.test");
        MaintenanceRequestEntity existing = existingRequest(room, existingReporter,
                "NHE", DESCRIPTION, IDEMPOTENCY_KEY);
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room), Optional.of(room));
        when(maintenanceRequestRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(userRepository.findWithRoleById(REPORTER_ID)).thenReturn(Optional.of(existingReporter));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceRequestRepository.saveAndFlush(any(MaintenanceRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.report(ROOM_ID, request("NHE", DESCRIPTION), IDEMPOTENCY_KEY,
                REPORTER_ID, "housekeeper@example.test", "127.0.0.1");
        RoomResponse replay = service.report(ROOM_ID, request("NHE", DESCRIPTION), IDEMPOTENCY_KEY,
                REPORTER_ID, "housekeeper@example.test", "127.0.0.1");

        assertThat(replay.status()).isEqualTo(RoomStatus.BAO_TRI);
        verify(maintenanceRequestRepository, times(1)).saveAndFlush(any(MaintenanceRequestEntity.class));
        verify(roomStatusHistoryRepository, times(1)).save(any(RoomStatusHistory.class));
        verify(auditLogService, times(1)).recordSensitiveAction(
                eq(REPORTER_ID), any(), isNull(), any(), any(), any());
        verify(notificationWriterService, times(1)).writeMaintenanceReported(any());
        verify(transactionManager, times(2)).commit(transactionStatus);
    }

    @Test
    void conflictsWhenExistingKeyHasDifferentPayload() {
        assertConflictForExistingKey("OTHER DESCRIPTION", REPORTER_ID);
    }

    @Test
    void conflictsWhenExistingKeyBelongsToDifferentUser() {
        assertConflictForExistingKey(DESCRIPTION, REPORTER_ID + 1);
    }

    @Test
    void differentKeyConflictsWhenRoomIsAlreadyInMaintenance() {
        UUID differentKey = UUID.randomUUID();
        room.setStatus(RoomStatus.BAO_TRI);
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));
        when(maintenanceRequestRepository.findByIdempotencyKey(differentKey))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.report(
                ROOM_ID, request("NHE", DESCRIPTION), differentKey,
                REPORTER_ID, "actor@example.test", "127.0.0.1"))
                .isInstanceOf(RoomStatusConflictException.class);

        verify(maintenanceRequestRepository, never()).saveAndFlush(any());
        verify(roomStatusHistoryRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
        verifyNoInteractions(notificationWriterService);
    }

    @Test
    void rollsBackTransactionWhenRequestWriteFails() {
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));
        when(maintenanceRequestRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty(), Optional.empty());
        when(userRepository.findWithRoleById(REPORTER_ID)).thenReturn(Optional.of(reporter));
        when(roomRepository.save(room)).thenReturn(room);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("write failed"))
                .when(maintenanceRequestRepository).saveAndFlush(any(MaintenanceRequestEntity.class));

        assertThatThrownBy(() -> service.report(
                ROOM_ID, request("NHE", DESCRIPTION), IDEMPOTENCY_KEY,
                REPORTER_ID, "actor@example.test", "127.0.0.1"))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any(TransactionStatus.class));
        verify(roomStatusHistoryRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
        verifyNoInteractions(notificationWriterService);
    }

    @Test
    void rollsBackTransactionWhenAuditWriteFails() {
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));
        when(maintenanceRequestRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(userRepository.findWithRoleById(REPORTER_ID)).thenReturn(Optional.of(reporter));
        when(roomRepository.save(room)).thenReturn(room);
        org.mockito.Mockito.doThrow(new IllegalStateException("audit failed"))
                .when(auditLogService).recordSensitiveAction(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.report(
                ROOM_ID, request("NHE", DESCRIPTION), IDEMPOTENCY_KEY,
                REPORTER_ID, "actor@example.test", "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit failed");

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any(TransactionStatus.class));
        verify(maintenanceRequestRepository).saveAndFlush(any(MaintenanceRequestEntity.class));
        verify(roomStatusHistoryRepository).save(any(RoomStatusHistory.class));
        verifyNoInteractions(notificationWriterService);
    }

    @Test
    void propagatesNotificationFailureAndRollsBackTransaction() {
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));
        when(maintenanceRequestRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty(), Optional.empty());
        when(userRepository.findWithRoleById(REPORTER_ID)).thenReturn(Optional.of(reporter));
        when(roomRepository.save(room)).thenReturn(room);
        when(maintenanceRequestRepository.saveAndFlush(any(MaintenanceRequestEntity.class)))
                .thenAnswer(invocation -> {
                    MaintenanceRequestEntity saved = invocation.getArgument(0);
                    saved.setId(101L);
                    return saved;
                });
        org.mockito.Mockito.doThrow(new IllegalStateException("notification failed"))
                .when(notificationWriterService).writeMaintenanceReported(any());

        assertThatThrownBy(() -> service.report(
                ROOM_ID, request("NHE", DESCRIPTION), IDEMPOTENCY_KEY,
                REPORTER_ID, "actor@example.test", "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notification failed");

        verify(notificationWriterService).writeMaintenanceReported(any());
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any(TransactionStatus.class));
    }

    private void assertConflictForExistingKey(String existingDescription, Long retryReporterId) {
        User existingReporter = reporter(REPORTER_ID, "Housekeeper", "housekeeper@example.test");
        Room maintainedRoom = roomInMaintenance();
        MaintenanceRequestEntity existing = existingRequest(maintainedRoom, existingReporter,
                "NHE", existingDescription, IDEMPOTENCY_KEY);
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(maintainedRoom));
        when(maintenanceRequestRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.report(
                ROOM_ID, request("NHE", DESCRIPTION), IDEMPOTENCY_KEY,
                retryReporterId, "actor@example.test", "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(maintenanceRequestRepository, never()).saveAndFlush(any());
        verify(roomStatusHistoryRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
        verifyNoInteractions(notificationWriterService);
    }

    private static IncidentReportRequest request(String severity, String description) {
        return new IncidentReportRequest(severity, description);
    }

    private static User reporter(Long id, String name, String email) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getFullName()).thenReturn(name);
        when(user.getEmail()).thenReturn(email);
        return user;
    }

    private static Room roomInMaintenance() {
        Room room = new Room();
        room.setId(ROOM_ID);
        room.setRoomNumber("204");
        room.setFloor(2);
        room.setRoomType("Double");
        room.setActive(true);
        room.setStatus(RoomStatus.BAO_TRI);
        return room;
    }

    private static MaintenanceRequestEntity existingRequest(
            Room room, User reporter, String severity, String description, UUID idempotencyKey) {
        MaintenanceRequestEntity existing = new MaintenanceRequestEntity();
        existing.setRoom(room);
        existing.setReportedBy(reporter);
        existing.setSeverity(severity);
        existing.setDescription(description);
        existing.setStatus("CHO_TIEP_NHAN");
        existing.setIdempotencyKey(idempotencyKey);
        existing.setRequestHash(hash(ROOM_ID, severity, description));
        return existing;
    }

    private static String hash(Long roomId, String severity, String description) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((roomId + "\n" + severity + "\n" + description)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
