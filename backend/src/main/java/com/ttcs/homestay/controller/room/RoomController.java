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
import com.ttcs.homestay.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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

    public RoomController(
            RoomService roomService,
            AuditLogService auditLogService) {
        this.roomService = roomService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<RoomResponse> getRooms(Authentication authentication) {
        List<RoomResponse> rooms = roomService.getRooms();

        if (isHousekeeping(authentication)) {
            return rooms.stream()
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

            if (room.status() != RoomStatus.TRONG_BAN
                    || request.status() != RoomStatus.TRONG_SACH) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Housekeeping chỉ được chuyển phòng Trống bẩn sang Trống sạch");
            }
        }

        RoomResponse response = roomService.updateStatus(
                roomId,
                request.status(),
                operatorName(authentication)
        );

        recordRoomAction(
                authentication,
                roomId,
                "ROOM_STATUS_UPDATED",
                httpRequest
        );

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
            HttpServletRequest httpRequest) {

        // Kiểm tra bảo vệ tầng controller: chỉ HOUSEKEEPING mới được dùng endpoint này
        if (!isHousekeeping(authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Chỉ nhân viên buồng phòng mới có thể báo sự cố.");
        }

        RoomResponse response = roomService.reportIncident(
                roomId,
                request,
                operatorName(authentication)
        );

        recordRoomAction(
                authentication,
                roomId,
                "ROOM_INCIDENT_REPORTED",
                httpRequest
        );

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