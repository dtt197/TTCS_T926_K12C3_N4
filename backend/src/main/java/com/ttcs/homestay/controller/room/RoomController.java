package com.ttcs.homestay.controller.room;

import com.ttcs.homestay.dto.CheckInRequest;
import com.ttcs.homestay.dto.CheckInResponse;
import com.ttcs.homestay.dto.IncidentReportRequest;
import com.ttcs.homestay.dto.MaintenanceRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.dto.RoomStatusHistoryResponse;
import com.ttcs.homestay.dto.UpdateRoomStatusRequest;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.service.AuditLogService;
import com.ttcs.homestay.service.IncidentReportService;
import com.ttcs.homestay.service.RoomService;
import com.ttcs.homestay.service.MaintenanceWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final AuditLogService auditLogService;
    private final IncidentReportService incidentReportService;
    private final MaintenanceWorkflowService maintenanceWorkflowService;

    @Autowired
    public RoomController(
            RoomService roomService,
            AuditLogService auditLogService,
            IncidentReportService incidentReportService,
            MaintenanceWorkflowService maintenanceWorkflowService) {
        this.roomService = roomService;
        this.auditLogService = auditLogService;
        this.incidentReportService = incidentReportService;
        this.maintenanceWorkflowService = maintenanceWorkflowService;
    }

    /** Compatibility constructor for existing controller unit tests. */
    public RoomController(RoomService roomService, AuditLogService auditLogService) {
        this(roomService, auditLogService, null, null);
    }

    public RoomController(RoomService roomService, AuditLogService auditLogService,
            IncidentReportService incidentReportService) {
        this(roomService, auditLogService, incidentReportService, null);
    }

    @GetMapping
    public List<RoomResponse> getRooms(Authentication authentication) {
        List<RoomResponse> rooms = roomService.getRooms();

        if (isHousekeeping(authentication)) {
            List<RoomResponse> dirtyRooms = rooms.stream()
                    .filter(room -> room.status() == RoomStatus.TRONG_BAN)
                    .sorted((a, b) -> {
                        boolean aToday = Boolean.TRUE.equals(a.hasGuestCheckInToday());
                        boolean bToday = Boolean.TRUE.equals(b.hasGuestCheckInToday());
                        if (aToday != bToday) {
                            return aToday ? -1 : 1;
                        }
                        if (a.expectedCheckInTime() != null && b.expectedCheckInTime() != null) {
                            int cmp = a.expectedCheckInTime().compareTo(b.expectedCheckInTime());
                            if (cmp != 0) return cmp;
                        }
                        return a.roomNumber().compareTo(b.roomNumber());
                    })
                    .toList();
            java.util.Set<Long> cleaningRoomIds = new java.util.HashSet<>(
                    maintenanceWorkflowService.cleaningRoomIds(jwt(authentication)));
            List<RoomResponse> maintenanceCleaningRooms = rooms.stream()
                    .filter(room -> cleaningRoomIds.contains(room.id()))
                    .toList();
            return java.util.stream.Stream
        .concat(dirtyRooms.stream(), maintenanceCleaningRooms.stream())
        .collect(java.util.stream.Collectors.toMap(
                RoomResponse::id,
                java.util.function.Function.identity(),
                (existing, duplicate) -> existing,
                java.util.LinkedHashMap::new
        ))
        .values()
        .stream()
        .toList();
        }

        return rooms;
    }

    @GetMapping("/{roomId}/history")
    public List<RoomStatusHistoryResponse> getHistory(
            @PathVariable Long roomId) {
        return roomService.getHistory(roomId);
    }

    @PatchMapping("/{roomId}/status")
    public RoomResponse updateStatus(
            @PathVariable Long roomId,
            @Valid @RequestBody UpdateRoomStatusRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        if (isHousekeeping(authentication)) {
            RoomResponse room = roomService.getRooms().stream()
                    .filter(candidate -> candidate.id().equals(roomId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Không có quyền thao tác phòng này"));

            if ((room.status() != RoomStatus.TRONG_BAN && room.status() != RoomStatus.BAO_TRI)
                    || request.status() != RoomStatus.TRONG_SACH) {
                if (roomService.hasOpenMaintenanceRequest(roomId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Phòng có yêu cầu vệ sinh sau bảo trì; hãy dùng thao tác Đã dọn sạch trong danh sách.");
                }
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Housekeeping chỉ được chuyển phòng Trống bẩn hoặc phòng bảo trì sang Trống sạch");
            }
            if (room.status() == RoomStatus.TRONG_BAN && roomService.hasOpenMaintenanceRequest(roomId))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phòng có yêu cầu vệ sinh sau bảo trì; hãy dùng thao tác Đã dọn sạch trong danh sách.");
        }

        RoomResponse response;
        if (isHousekeeping(authentication)) {
            response = roomService.updateStatusWithAuditAndNotification(
                    roomId,
                    request.status(),
                    operatorName(authentication),
                    actorUserId(authentication),
                    actorEmail(authentication),
                    httpRequest.getRemoteAddr());
        } else {
            response = roomService.updateStatus(
                    roomId,
                    request.status(),
                    operatorName(authentication));
            recordRoomAction(
                    authentication,
                    roomId,
                    "ROOM_STATUS_UPDATED",
                    httpRequest);
        }

        return response;
    }

    @PatchMapping("/{roomId}/maintenance")
    public RoomResponse putIntoMaintenance(
            @PathVariable Long roomId,
            @Valid @RequestBody MaintenanceRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        RoomResponse response = roomService.putIntoMaintenance(
                roomId,
                request,
                operatorName(authentication)
        );

        recordRoomAction(
                authentication,
                roomId,
                "ROOM_MAINTENANCE_UPDATED",
                httpRequest
        );

        return response;
    }

    /**
     * S3-09 AC4: Buồng phòng báo sự cố cho phòng TRONG_BAN (không cần chọn ngày).
     * Ghi chú rỗng bị từ chối 400.
     */
    @PatchMapping("/{roomId}/incident")
    public RoomResponse reportIncident(
            @PathVariable Long roomId,
            @Valid @RequestBody IncidentReportRequest request,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            HttpServletRequest httpRequest) {

        // Kiểm tra bảo vệ tầng controller: chỉ HOUSEKEEPING mới được dùng endpoint này
        if (!isHousekeeping(authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Chỉ nhân viên buồng phòng mới có thể báo sự cố.");
        }

        UUID idempotencyKey;
        try {
            if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
                throw new IllegalArgumentException("missing");
            }
            idempotencyKey = UUID.fromString(idempotencyKeyHeader.trim());
            if (!idempotencyKey.toString().equalsIgnoreCase(idempotencyKeyHeader.trim())) {
                throw new IllegalArgumentException("invalid UUID format");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Header Idempotency-Key là bắt buộc và phải là UUID hợp lệ.");
        }
        if (incidentReportService == null) {
            throw new IllegalStateException("IncidentReportService is required");
        }
        return incidentReportService.report(
                roomId,
                request,
                idempotencyKey,
                actorUserId(authentication),
                actorEmail(authentication),
                httpRequest.getRemoteAddr());
    }

    /** Legacy direct-call seam for existing controller unit tests; not mapped as an HTTP handler. */
    public RoomResponse reportIncident(
            Long roomId,
            IncidentReportRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        if (!isHousekeeping(authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Chỉ nhân viên buồng phòng mới có thể báo sự cố.");
        }
        RoomResponse response = roomService.reportIncident(
                roomId, request, operatorName(authentication));
        recordRoomAction(authentication, roomId, "ROOM_INCIDENT_REPORTED", httpRequest);
        return response;
    }

    @PostMapping("/{roomId}/check-in")
    public ResponseEntity<CheckInResponse> checkIn(
            @PathVariable Long roomId,
            @Valid @RequestBody CheckInRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        CheckInResponse response = roomService.checkIn(
                roomId,
                request,
                operatorName(authentication)
        );

        recordRoomAction(
                authentication,
                roomId,
                "ROOM_CHECKED_IN",
                httpRequest
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/check-out")
    public ResponseEntity<RoomResponse> checkOut(
            @PathVariable Long roomId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        RoomResponse response = roomService.checkOut(
                roomId,
                operatorName(authentication)
        );

        recordRoomAction(
                authentication,
                roomId,
                "ROOM_CHECKED_OUT",
                httpRequest
        );

        return ResponseEntity.ok(response);
    }

    private void recordRoomAction(
            Authentication authentication,
            Long roomId,
            String action,
            HttpServletRequest httpRequest) {

        auditLogService.recordSensitiveAction(
                actorUserId(authentication),
                actorEmail(authentication),
                null,
                "Phòng #" + roomId,
                action,
                httpRequest.getRemoteAddr()
        );
    }

    private static Long actorUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            try {
                return Long.valueOf(token.getToken().getSubject());
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        return null;
    }

    private static String actorEmail(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            String email = token.getToken().getClaimAsString("email");

            if (email != null && !email.isBlank()) {
                return email;
            }
        }

        return authentication.getName();
    }

    private static org.springframework.security.oauth2.jwt.Jwt jwt(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken token ? token.getToken() : null;
    }

    private static boolean isHousekeeping(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_HOUSEKEEPING".equals(authority.getAuthority()));
    }

    static String operatorName(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            String fullName =
                    token.getToken().getClaimAsString("fullName");

            if (fullName != null && !fullName.isBlank()) {
                return fullName;
            }

            String email =
                    token.getToken().getClaimAsString("email");

            if (email != null && !email.isBlank()) {
                return email;
            }
        }

        return authentication.getName();
    }
}
