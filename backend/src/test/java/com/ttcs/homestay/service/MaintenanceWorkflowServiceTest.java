package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.maintenance.VendorRequest;
import com.ttcs.homestay.dto.maintenance.MaintenanceNoteRequest;
import com.ttcs.homestay.entity.MaintenanceRequestEntity;
import com.ttcs.homestay.entity.MaintenanceRequestHistory;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.MaintenanceRequestHistoryRepository;
import com.ttcs.homestay.repository.MaintenanceRequestRepository;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MaintenanceWorkflowServiceTest {
    @Mock MaintenanceRequestRepository requests;
    @Mock MaintenanceRequestHistoryRepository history;
    @Mock UserRepository users;
    @Mock AuditLogService audit;
    @Mock RoomRepository rooms;
    @Mock RoomStatusHistoryRepository roomHistory;
    @Mock NotificationWriterService notifications;

    private MaintenanceWorkflowService service;
    private Jwt ownerJwt;
    private Jwt receptionistJwt;
    private Jwt housekeepingJwt;
    private User owner;
    private MaintenanceRequestEntity request;
    private Room room;

    @BeforeEach
    void setUp() {
        service = new MaintenanceWorkflowService(requests, history, users, audit, rooms, roomHistory, notifications);
        ownerJwt = jwt("10", "OWNER");
        receptionistJwt = jwt("11", "RECEPTIONIST");
        housekeepingJwt = jwt("12", "HOUSEKEEPING");
        owner = org.mockito.Mockito.mock(User.class);
        request = new MaintenanceRequestEntity();
        request.setId(42L);
        request.setStatus("CHO_TIEP_NHAN");
        request.setSeverity("NGHIEM_TRONG");
        request.setDescription("Rò nước trong phòng tắm");
        request.setReportedAt(OffsetDateTime.parse("2026-10-01T10:00:00Z"));
        room = new Room();
        room.setId(105L);
        room.setRoomNumber("105");
        room.setMaintenanceReason("Đang sửa chữa");
        request.setRoom(room);
        User reporter = org.mockito.Mockito.mock(User.class);
        request.setReportedBy(reporter);
    }

    @Test
    void ownerCanReadListAndDetailWithHistory() {
        org.mockito.Mockito.doReturn(20L).when(request.getReportedBy()).getId();
        org.mockito.Mockito.doReturn("Reporter").when(request.getReportedBy()).getFullName();
        var entry = new MaintenanceRequestHistory();
        allowOwnerRead();
        stubOwnerAuthorization("OWNER");
        when(requests.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(request)));
        when(requests.findById(42L)).thenReturn(Optional.of(request));
        when(history.findByMaintenanceRequestIdOrderByOccurredAtAscIdAsc(42L)).thenReturn(List.of(entry));

        assertThat(service.list(ownerJwt, 0, 20).content()).hasSize(1);
        assertThat(service.detail(ownerJwt, 42L).history()).containsExactly(entry);
        verify(history).findByMaintenanceRequestIdOrderByOccurredAtAscIdAsc(42L);
    }

    @Test
    void ownerAcceptsRequestAndRecordsHistoryAndAuditWithoutChangingRoom() {
        stubReporterForResponse();
        allowOwnerWrite();
        stubOwnerAuthorization("OWNER");
        org.mockito.Mockito.doReturn(10L).when(owner).getId();
        org.mockito.Mockito.doReturn("User 10").when(owner).getFullName();
        org.mockito.Mockito.doReturn("user10@example.test").when(owner).getEmail();
        var response = service.accept(ownerJwt, 42L, "127.0.0.1");

        assertThat(response.status()).isEqualTo("DA_TIEP_NHAN");
        assertThat(request.getAcceptedBy()).isSameAs(owner);
        assertThat(request.getAcceptedAt()).isNotNull();
        assertThat(room.getMaintenanceReason()).isEqualTo("Đang sửa chữa");
        verify(history).save(anyHistory("ACCEPTED", "CHO_TIEP_NHAN", "DA_TIEP_NHAN"));
        verify(audit).recordSensitiveActionInCurrentTransaction(10L, "user10@example.test", null,
                "Maintenance request #42", "MAINTENANCE_ACCEPTED", "127.0.0.1");
    }

    @Test
    void ownerUpdatesExternalVendorWithoutChangingWorkflowStatus() {
        stubReporterForResponse();
        allowOwnerWrite();
        stubOwnerAuthorization("OWNER");
        org.mockito.Mockito.doReturn(10L).when(owner).getId();
        org.mockito.Mockito.doReturn("User 10").when(owner).getFullName();
        org.mockito.Mockito.doReturn("user10@example.test").when(owner).getEmail();
        request.setStatus("DA_TIEP_NHAN");
        var response = service.updateVendor(ownerJwt, 42L,
                new VendorRequest(" Công ty Sửa chữa ", " 0900-123-456 ", " Gọi trước "), "10.0.0.1");

        assertThat(response.status()).isEqualTo("DA_TIEP_NHAN");
        assertThat(response.vendorName()).isEqualTo("Công ty Sửa chữa");
        assertThat(response.vendorPhone()).isEqualTo("0900-123-456");
        assertThat(response.vendorNotes()).isEqualTo("Gọi trước");
        verify(history).save(anyHistory("VENDOR_UPDATED", "DA_TIEP_NHAN", "DA_TIEP_NHAN"));
        verify(audit).recordSensitiveActionInCurrentTransaction(10L, "user10@example.test", null,
                "Maintenance request #42", "MAINTENANCE_VENDOR_UPDATED", "10.0.0.1");
    }

    @Test
    void ownerStartsProcessingAndCapturesVendorInHistory() {
        stubReporterForResponse();
        allowOwnerWrite();
        stubOwnerAuthorization("OWNER");
        org.mockito.Mockito.doReturn(10L).when(owner).getId();
        org.mockito.Mockito.doReturn("User 10").when(owner).getFullName();
        org.mockito.Mockito.doReturn("user10@example.test").when(owner).getEmail();
        request.setStatus("DA_TIEP_NHAN");
        var response = service.start(ownerJwt, 42L,
                new VendorRequest("Đơn vị ngoài", "+84 900 123 456", "Đã liên hệ"), null);

        assertThat(response.status()).isEqualTo("DANG_XU_LY");
        assertThat(response.processingStartedAt()).isNotNull();
        assertThat(room.getMaintenanceReason()).isEqualTo("Đang sửa chữa");
        verify(history).save(anyHistory("PROCESSING_STARTED", "DA_TIEP_NHAN", "DANG_XU_LY"));
        verify(audit).recordSensitiveActionInCurrentTransaction(10L, "user10@example.test", null,
                "Maintenance request #42", "MAINTENANCE_PROCESSING_STARTED", null);
    }

    @Test
    void ownerReportsRepairCompleteWithActorTimeNotesHistoryAndAudit() {
        stubReporterForResponse(); allowOwnerWrite(); stubOwnerAuthorization("OWNER");
        org.mockito.Mockito.doReturn(10L).when(owner).getId();
        org.mockito.Mockito.doReturn("User 10").when(owner).getFullName();
        org.mockito.Mockito.doReturn("user10@example.test").when(owner).getEmail();
        request.setStatus("DANG_XU_LY");
        var response = service.repairCompleted(ownerJwt, 42L, new MaintenanceNoteRequest("Đã thay van rò nước"), "ip");
        assertThat(response.status()).isEqualTo("CHO_XAC_NHAN");
        assertThat(response.repairCompletedById()).isEqualTo(10L);
        assertThat(response.repairCompletedAt()).isNotNull();
        assertThat(response.repairNotes()).isEqualTo("Đã thay van rò nước");
        verify(history).save(anyHistory("REPAIR_COMPLETED", "DANG_XU_LY", "CHO_XAC_NHAN"));
        verify(audit).recordSensitiveActionInCurrentTransaction(10L, "user10@example.test", null,
                "Maintenance request #42", "MAINTENANCE_REPAIR_COMPLETED", "ip");
    }

    @Test
    void ownerCanVerifyPassOrRejectRepairAndWritesResult() {
        stubReporterForResponse(); allowOwnerWrite(); stubOwnerAuthorization("OWNER");
        org.mockito.Mockito.doReturn(10L).when(owner).getId();
        org.mockito.Mockito.doReturn("User 10").when(owner).getFullName();
        org.mockito.Mockito.doReturn("user10@example.test").when(owner).getEmail();
        request.setStatus("CHO_XAC_NHAN");
        room.setStatus(com.ttcs.homestay.entity.RoomStatus.BAO_TRI);
        when(rooms.findByIdForUpdate(105L)).thenReturn(Optional.of(room));
        assertThat(service.verify(ownerJwt, 42L, true, new MaintenanceNoteRequest("Đạt"), "ip").status())
                .isEqualTo("CHO_VE_SINH");
        assertThat(room.getStatus()).isEqualTo(com.ttcs.homestay.entity.RoomStatus.TRONG_BAN);
        verify(history).save(anyHistory("VERIFIED", "CHO_XAC_NHAN", "CHO_VE_SINH"));
        verify(audit).recordSensitiveActionInCurrentTransaction(10L, "user10@example.test", null,
                "Maintenance request #42", "MAINTENANCE_VERIFIED", "ip");

        request.setStatus("CHO_XAC_NHAN");
        room.setStatus(com.ttcs.homestay.entity.RoomStatus.BAO_TRI);
        when(requests.findByIdForUpdate(42L)).thenReturn(Optional.of(request));
        assertThat(service.verify(ownerJwt, 42L, false, new MaintenanceNoteRequest("Vẫn còn rò, thay lại gioăng"), "ip").status())
                .isEqualTo("DANG_XU_LY");
        verify(history).save(anyHistory("REPAIR_REJECTED", "CHO_XAC_NHAN", "DANG_XU_LY"));
        verify(audit).recordSensitiveActionInCurrentTransaction(10L, "user10@example.test", null,
                "Maintenance request #42", "MAINTENANCE_REPAIR_REJECTED", "ip");
    }

    @Test
    void receptionistAndHousekeepingCannotCompleteOrVerifyRepair() {
        assertThatThrownBy(() -> service.repairCompleted(receptionistJwt, 42L, new MaintenanceNoteRequest("x"), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.verify(housekeepingJwt, 42L, true, new MaintenanceNoteRequest("x"), null))
                .isInstanceOf(AccessDeniedException.class);
        verify(requests, never()).findByIdForUpdate(any());
        verify(history, never()).save(any());
    }

    @Test
    void repairTransitionsRejectWrongStateAndEmptyResult() {
        allowOwnerWrite(); stubOwnerAuthorization("OWNER"); request.setStatus("DA_TIEP_NHAN");
        assertThatThrownBy(() -> service.repairCompleted(ownerJwt, 42L, new MaintenanceNoteRequest("x"), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409);
        request.setStatus("CHO_XAC_NHAN");
        when(requests.findByIdForUpdate(42L)).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.verify(ownerJwt, 42L, false, new MaintenanceNoteRequest(" "), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value()).isEqualTo(400);
        verify(history, never()).save(any());
        verify(audit, never()).recordSensitiveActionInCurrentTransaction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void receptionistCanReadButCannotWrite() {
        User receptionist = org.mockito.Mockito.mock(User.class);
        stubUserAuthorization(receptionist, "RECEPTIONIST");
        when(users.findWithRoleById(11L)).thenReturn(Optional.of(receptionist));
        when(requests.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(request)));
        assertThat(service.list(receptionistJwt, 0, 20).content()).hasSize(1);

        assertThatThrownBy(() -> service.accept(receptionistJwt, 42L, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(requests, never()).findByIdForUpdate(any());
        verify(history, never()).save(any());
        verify(audit, never()).recordSensitiveActionInCurrentTransaction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void housekeepingCannotManageRequests() {
        var cleaner = org.mockito.Mockito.mock(User.class);
        stubUserAuthorization(cleaner, "HOUSEKEEPING");
        when(users.findWithRoleById(12L)).thenReturn(Optional.of(cleaner));
        when(requests.findByStatus(eq("CHO_VE_SINH"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(request)));
        when(requests.findById(42L)).thenReturn(Optional.of(request));
        assertThat(service.list(housekeepingJwt, 0, 20).content()).hasSize(1);
        assertThatThrownBy(() -> service.detail(housekeepingJwt, 42L)).isInstanceOf(ResponseStatusException.class);
        verify(requests, never()).findAll(any(PageRequest.class));
    }

    @Test
    void housekeepingCompletesCleaningAtomicallyWithRoomHistoryAuditAndNotification() {
        var cleaner = org.mockito.Mockito.mock(User.class);
        stubUserAuthorization(cleaner, "HOUSEKEEPING");
        org.mockito.Mockito.doReturn(12L).when(cleaner).getId();
        org.mockito.Mockito.doReturn("Cleaner").when(cleaner).getFullName();
        org.mockito.Mockito.doReturn("cleaner@example.test").when(cleaner).getEmail();
        when(users.findWithRoleById(12L)).thenReturn(Optional.of(cleaner));
        when(requests.findByIdForUpdate(42L)).thenReturn(Optional.of(request));
        when(rooms.findByIdForUpdate(105L)).thenReturn(Optional.of(room));
        request.setStatus("CHO_VE_SINH");
        room.setStatus(com.ttcs.homestay.entity.RoomStatus.BAO_TRI);
        stubReporterForResponse();
        var response = service.cleaningCompleted(housekeepingJwt, 42L,
                new MaintenanceNoteRequest("Phòng đã sạch"), "ip");
        assertThat(response.status()).isEqualTo("HOAN_TAT");
        assertThat(response.cleanedById()).isEqualTo(12L);
        assertThat(response.cleaningNotes()).isEqualTo("Phòng đã sạch");
        assertThat(response.completedAt()).isNotNull();
        assertThat(room.getStatus()).isEqualTo(com.ttcs.homestay.entity.RoomStatus.TRONG_SACH);
        verify(roomHistory).save(any(com.ttcs.homestay.entity.RoomStatusHistory.class));
        verify(history).save(anyHistory("CLEANING_COMPLETED", "CHO_VE_SINH", "HOAN_TAT", 12L,
                "Phòng đã sạch"));
        verify(audit).recordSensitiveActionInCurrentTransaction(12L, "cleaner@example.test", null,
                "Maintenance request #42", "MAINTENANCE_CLEANING_COMPLETED", "ip");
        verify(notifications).writeMaintenanceCleaned(request);
    }

    @Test
    void housekeepingCanSafelyCompleteLegacyRequestWhileRoomIsStillUnderMaintenance() {
        var cleaner = org.mockito.Mockito.mock(User.class);
        stubUserAuthorization(cleaner, "HOUSEKEEPING");
        org.mockito.Mockito.doReturn(12L).when(cleaner).getId();
        org.mockito.Mockito.doReturn("Cleaner").when(cleaner).getFullName();
        org.mockito.Mockito.doReturn("cleaner@example.test").when(cleaner).getEmail();
        when(users.findWithRoleById(12L)).thenReturn(Optional.of(cleaner));
        when(requests.findByIdForUpdate(42L)).thenReturn(Optional.of(request));
        when(rooms.findByIdForUpdate(105L)).thenReturn(Optional.of(room));
        request.setStatus("CHO_VE_SINH");
        room.setStatus(com.ttcs.homestay.entity.RoomStatus.BAO_TRI);
        stubReporterForResponse();

        service.cleaningCompleted(housekeepingJwt, 42L, new MaintenanceNoteRequest("Đã vệ sinh"), "ip");

        var statusHistory = org.mockito.ArgumentCaptor.forClass(com.ttcs.homestay.entity.RoomStatusHistory.class);
        verify(roomHistory).save(statusHistory.capture());
        assertThat(statusHistory.getValue().getPreviousStatus()).isEqualTo(com.ttcs.homestay.entity.RoomStatus.BAO_TRI);
        assertThat(statusHistory.getValue().getNewStatus()).isEqualTo(com.ttcs.homestay.entity.RoomStatus.TRONG_SACH);
    }

    @Test
    void cleaningRejectsRoomAlreadyDirtyToPreventClosingRequestForWrongRoomState() {
        var cleaner = org.mockito.Mockito.mock(User.class);
        stubUserAuthorization(cleaner, "HOUSEKEEPING");
        when(users.findWithRoleById(12L)).thenReturn(Optional.of(cleaner));
        when(requests.findByIdForUpdate(42L)).thenReturn(Optional.of(request));
        when(rooms.findByIdForUpdate(105L)).thenReturn(Optional.of(room));
        request.setStatus("CHO_VE_SINH");
        room.setStatus(com.ttcs.homestay.entity.RoomStatus.TRONG_BAN);
        when(requests.existsOtherByRoomIdAndStatusNot(105L, "HOAN_TAT", 42L)).thenReturn(false);
        stubReporterForResponse();
        service.cleaningCompleted(housekeepingJwt, 42L, new MaintenanceNoteRequest("Done"), null);
        var statusHistory = org.mockito.ArgumentCaptor.forClass(com.ttcs.homestay.entity.RoomStatusHistory.class);
        verify(roomHistory).save(statusHistory.capture());
        assertThat(statusHistory.getValue().getPreviousStatus()).isEqualTo(com.ttcs.homestay.entity.RoomStatus.TRONG_BAN);
    }

    @Test
    void cleaningRejectsWrongRoomOrAnotherOpenRequestWithConflict() {
        var cleaner = org.mockito.Mockito.mock(User.class);
        stubUserAuthorization(cleaner, "HOUSEKEEPING");
        when(users.findWithRoleById(12L)).thenReturn(Optional.of(cleaner));
        when(requests.findByIdForUpdate(42L)).thenReturn(Optional.of(request));
        when(rooms.findByIdForUpdate(105L)).thenReturn(Optional.of(room));
        request.setStatus("CHO_VE_SINH");
        room.setStatus(com.ttcs.homestay.entity.RoomStatus.TRONG_SACH);
        when(requests.existsOtherByRoomIdAndStatusNot(105L, "HOAN_TAT", 42L)).thenReturn(false);
        assertThatThrownBy(() -> service.cleaningCompleted(housekeepingJwt, 42L,
                new MaintenanceNoteRequest("Done"), null)).isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409);

        org.mockito.Mockito.clearInvocations(history, roomHistory, audit, notifications);
        room.setStatus(com.ttcs.homestay.entity.RoomStatus.BAO_TRI);
        when(requests.existsOtherByRoomIdAndStatusNot(105L, "HOAN_TAT", 42L)).thenReturn(true);
        assertThatThrownBy(() -> service.cleaningCompleted(housekeepingJwt, 42L,
                new MaintenanceNoteRequest("Done"), null)).isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409);
        verify(history, never()).save(any());
        verify(roomHistory, never()).save(any());
        verify(audit, never()).recordSensitiveActionInCurrentTransaction(any(), any(), any(), any(), any(), any());
        verify(notifications, never()).writeMaintenanceCleaned(any());
    }

    @Test
    void invalidOrRepeatedTransitionReturnsConflictWithoutAuditOrHistory() {
        allowOwnerWrite();
        stubOwnerAuthorization("OWNER");
        request.setStatus("DA_TIEP_NHAN");
        assertThatThrownBy(() -> service.accept(ownerJwt, 42L, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409);
        verify(history, never()).save(any());
        verify(audit, never()).recordSensitiveActionInCurrentTransaction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void auditAndHistoryAreOnlyWrittenForAllowedOwnerTransition() {
        stubReporterForResponse();
        allowOwnerWrite();
        stubOwnerAuthorization("OWNER");
        org.mockito.Mockito.doReturn(10L).when(owner).getId();
        org.mockito.Mockito.doReturn("User 10").when(owner).getFullName();
        org.mockito.Mockito.doReturn("user10@example.test").when(owner).getEmail();
        request.setStatus("DA_TIEP_NHAN");
        service.start(ownerJwt, 42L, new VendorRequest("Đơn vị", "0900123456", null), "ip");
        verify(history).save(any(MaintenanceRequestHistory.class));
        verify(audit).recordSensitiveActionInCurrentTransaction(eq(10L), eq("user10@example.test"), eq(null),
                eq("Maintenance request #42"), eq("MAINTENANCE_PROCESSING_STARTED"), eq("ip"));
    }

    private void allowOwnerRead() {
        when(users.findWithRoleById(10L)).thenReturn(Optional.of(owner));
    }

    private void stubReporterForResponse() {
        org.mockito.Mockito.doReturn(20L).when(request.getReportedBy()).getId();
        org.mockito.Mockito.doReturn("Reporter").when(request.getReportedBy()).getFullName();
    }

    private void stubOwnerAuthorization(String roleCode) {
        stubUserAuthorization(owner, roleCode);
    }

    private void stubUserAuthorization(User user, String roleCode) {
        Role role = org.mockito.Mockito.mock(Role.class);
        org.mockito.Mockito.doReturn(roleCode).when(role).getCode();
        org.mockito.Mockito.doReturn(role).when(user).getRole();
        org.mockito.Mockito.doReturn(true).when(user).isActive();
    }

    private void allowOwnerWrite() {
        allowOwnerRead();
        when(requests.findByIdForUpdate(42L)).thenReturn(Optional.of(request));
    }

    private static Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .claim("role", role).build();
    }

    private static MaintenanceRequestHistory anyHistory(String action, String previous, String next) {
        return org.mockito.ArgumentMatchers.argThat(entry -> entry != null
                && action.equals(entry.getAction()) && previous.equals(entry.getPreviousStatus())
                && next.equals(entry.getNewStatus()) && Long.valueOf(10L).equals(entry.getActorUserId()));
    }

    private static MaintenanceRequestHistory anyHistory(String action, String previous, String next,
            Long actorUserId, String notes) {
        return org.mockito.ArgumentMatchers.argThat(entry -> entry != null
                && action.equals(entry.getAction()) && previous.equals(entry.getPreviousStatus())
                && next.equals(entry.getNewStatus()) && actorUserId.equals(entry.getActorUserId())
                && notes.equals(entry.getNotes()) && entry.getOccurredAt() != null);
    }
}
