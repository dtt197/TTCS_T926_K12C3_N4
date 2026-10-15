package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.IncidentReportRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.entity.MaintenanceRequestEntity;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomStatusHistory;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.RoomNotFoundException;
import com.ttcs.homestay.exception.RoomStatusConflictException;
import com.ttcs.homestay.repository.MaintenanceRequestRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;
import com.ttcs.homestay.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creates incident maintenance requests and commits them atomically with room
 * status history and the matching audit record.
 */
@Service
public class IncidentReportService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String GENERIC_KEY_CONFLICT = "Idempotency-Key đã được sử dụng.";

    private final RoomRepository roomRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final RoomStatusHistoryRepository roomStatusHistoryRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationWriterService notificationWriterService;
    private final TransactionTemplate transactionTemplate;

    public IncidentReportService(
            RoomRepository roomRepository,
            MaintenanceRequestRepository maintenanceRequestRepository,
            RoomStatusHistoryRepository roomStatusHistoryRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            NotificationWriterService notificationWriterService,
            PlatformTransactionManager transactionManager) {
        this.roomRepository = roomRepository;
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.roomStatusHistoryRepository = roomStatusHistoryRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.notificationWriterService = notificationWriterService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * The transaction callback fully rolls back before a uniqueness race is
     * inspected. Other persistence failures are propagated without retry.
     */
    public RoomResponse report(
            Long roomId,
            IncidentReportRequest request,
            UUID idempotencyKey,
            Long reporterId,
            String actorEmail,
            String ipAddress) {
        String severity = request.severity().trim();
        String description = request.description().trim();
        if (!severity.equals("NHE") && !severity.equals("NGHIEM_TRONG")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Mức độ chỉ nhận NHE hoặc NGHIEM_TRONG");
        }
        if (description.isEmpty() || description.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Mô tả sự cố bắt buộc và tối đa 2000 ký tự");
        }
        if (idempotencyKey == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu header Idempotency-Key hợp lệ dạng UUID");
        }

        String requestHash = hash(roomId, severity, description);
        if (reporterId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Không xác định được tài khoản báo cáo");
        }
        try {
            return transactionTemplate.execute(status -> createOrReplay(
                    roomId, severity, description, idempotencyKey, requestHash,
                    reporterId, actorEmail, ipAddress));
        } catch (DataIntegrityViolationException exception) {
            // A competing transaction may have won this key. Query only after
            // TransactionTemplate has rolled back the failed transaction.
            MaintenanceRequestEntity existing = maintenanceRequestRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElse(null);
            if (existing == null) {
                throw exception;
            }
            if (!existing.getReportedBy().getId().equals(reporterId)
                    || !existing.getRequestHash().equals(requestHash)) {
                throw keyConflict();
            }
            return roomRepository.findById(roomId)
                    .map(RoomResponse::from)
                    .orElseThrow(() -> new RoomNotFoundException(roomId));
        }
    }

    private RoomResponse createOrReplay(
            Long roomId,
            String severity,
            String description,
            UUID idempotencyKey,
            String requestHash,
            Long reporterId,
            String actorEmail,
            String ipAddress) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        MaintenanceRequestEntity existing = maintenanceRequestRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!existing.getReportedBy().getId().equals(reporterId)
                    || !existing.getRequestHash().equals(requestHash)) {
                throw keyConflict();
            }
            return RoomResponse.from(existing.getRoom());
        }

        if (room.getStatus() != RoomStatus.TRONG_BAN) {
            throw new RoomStatusConflictException(
                    "Chỉ có thể báo sự cố cho phòng đang ở trạng thái Trống bẩn. Phòng "
                            + room.getRoomNumber() + " hiện không ở trạng thái Trống bẩn.");
        }

        // Room lock serializes new reports; replay above retains phase-1 semantics.
        if (maintenanceRequestRepository.existsByRoom_IdAndStatusNot(roomId, "HOAN_TAT")) {
            throw new RoomStatusConflictException("Phòng vẫn còn yêu cầu bảo trì chưa hoàn tất.");
        }

        User reporter = userRepository.findWithRoleById(reporterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Không xác định được tài khoản báo cáo"));
        RoomStatus previousStatus = room.getStatus();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        room.setStatus(RoomStatus.BAO_TRI);
        // rooms.maintenance_reason is a legacy VARCHAR(500); the full trimmed
        // description remains intact on maintenance_requests.
        room.setMaintenanceReason(description.substring(0, Math.min(description.length(), 500)));
        room.setMaintenanceStartDate(today);
        room.setMaintenanceEndDate(null);
        Room savedRoom = roomRepository.save(room);

        MaintenanceRequestEntity maintenanceRequest = new MaintenanceRequestEntity();
        maintenanceRequest.setRoom(savedRoom);
        maintenanceRequest.setSeverity(severity);
        maintenanceRequest.setDescription(description);
        maintenanceRequest.setStatus("CHO_TIEP_NHAN");
        maintenanceRequest.setReportedBy(reporter);
        maintenanceRequest.setReportedAt(OffsetDateTime.now(BUSINESS_ZONE));
        maintenanceRequest.setIdempotencyKey(idempotencyKey);
        maintenanceRequest.setRequestHash(requestHash);
        maintenanceRequestRepository.saveAndFlush(maintenanceRequest);

        RoomStatusHistory history = new RoomStatusHistory();
        history.setRoom(savedRoom);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(RoomStatus.BAO_TRI);
        history.setChangedBy(reporter.getFullName());
        history.setChangedAt(OffsetDateTime.now(BUSINESS_ZONE));
        history.setMaintenanceReason(savedRoom.getMaintenanceReason());
        history.setMaintenanceStartDate(today);
        roomStatusHistoryRepository.save(history);

        // recordSensitiveAction joins this transaction, so an audit failure
        // rolls back the request, room update, and history together.
        auditLogService.recordSensitiveAction(
                reporter.getId(),
                actorEmail == null || actorEmail.isBlank() ? reporter.getEmail() : actorEmail,
                null,
                "Phòng #" + roomId,
                "ROOM_INCIDENT_REPORTED",
                ipAddress);
        notificationWriterService.writeMaintenanceReported(maintenanceRequest);
        return RoomResponse.from(savedRoom);
    }

    private static ResponseStatusException keyConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, GENERIC_KEY_CONFLICT);
    }

    private static String hash(Long roomId, String severity, String description) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    (roomId + "\n" + severity + "\n" + description)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
