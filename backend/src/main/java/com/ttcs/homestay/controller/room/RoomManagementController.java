package com.ttcs.homestay.controller.room;

import com.ttcs.homestay.dto.CreatePhysicalRoomRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.dto.RoomSearchResponse;
import com.ttcs.homestay.dto.RoomUpdateResponse;
import com.ttcs.homestay.dto.UpdatePhysicalRoomRequest;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.service.AuditLogService;
import com.ttcs.homestay.service.RoomManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/room-management")
public class RoomManagementController {

    private final RoomManagementService roomManagementService;
    private final AuditLogService auditLogService;

    public RoomManagementController(
            RoomManagementService roomManagementService,
            AuditLogService auditLogService) {
        this.roomManagementService = roomManagementService;
        this.auditLogService = auditLogService;
    }

    /** SCRUM-9: tạo phòng vật lý. */
    @PostMapping("/rooms")
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreatePhysicalRoomRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        RoomResponse response = roomManagementService.create(request);

        recordRoomManagementAction(
                authentication,
                response,
                "ROOM_CREATED",
                httpRequest
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /** SCRUM-17: lọc kết hợp theo loại phòng, tầng, trạng thái. */
    @GetMapping("/rooms")
    public RoomSearchResponse searchRooms(
            @RequestParam(required = false) String roomType,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) RoomStatus status,
            @RequestParam(required = false) Boolean active) {

        return roomManagementService.search(
                roomType,
                floor,
                status,
                active
        );
    }

    /**
     * SCRUM-12: cập nhật thông tin phòng;
     * trả warning trước khi lưu nếu chưa kiểm tra được booking.
     */
   @PatchMapping("/rooms/{roomId}")
public RoomUpdateResponse updateRoom(
        @PathVariable Long roomId,
        @Valid @RequestBody UpdatePhysicalRoomRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest) {

    RoomUpdateResponse response =
            roomManagementService.update(roomId, request);

    // Request đầu chỉ kiểm tra/cảnh báo.
    // Chỉ request xác nhận cuối cùng mới được ghi nhật ký.
    if (request.confirmWhenBookingCheckUnavailable()) {
        recordRoomManagementAction(
                authentication,
                response.room(),
                "ROOM_UPDATED",
                httpRequest
        );
    }

    return response;
}

    @GetMapping("/rooms/{roomId}/note")
    public Map<String, String> getRoomNote(
            @PathVariable Long roomId) {
        return Map.of(
                "note",
                roomManagementService.getNote(roomId)
        );
    }

    private void recordRoomManagementAction(
            Authentication authentication,
            RoomResponse room,
            String action,
            HttpServletRequest httpRequest) {

        auditLogService.recordSensitiveAction(
                actorUserId(authentication),
                actorEmail(authentication),
                null,
                "Phòng " + room.roomNumber(),
                action,
                httpRequest.getRemoteAddr()
        );
    }

    private static Long actorUserId(
            Authentication authentication) {

        if (authentication instanceof JwtAuthenticationToken token) {
            try {
                return Long.valueOf(
                        token.getToken().getSubject()
                );
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        return null;
    }

    private static String actorEmail(
            Authentication authentication) {

        if (authentication instanceof JwtAuthenticationToken token) {
            String email =
                    token.getToken().getClaimAsString("email");

            if (email != null && !email.isBlank()) {
                return email;
            }
        }

        return authentication.getName();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException exception) {

        return ResponseEntity.badRequest().body(
                Map.of(
                        "code", "ROOM_MANAGEMENT_ERROR",
                        "message", exception.getMessage()
                )
        );
    }
}