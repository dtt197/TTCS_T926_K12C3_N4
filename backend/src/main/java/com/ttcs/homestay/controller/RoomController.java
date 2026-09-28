package com.ttcs.homestay.controller;

import com.ttcs.homestay.dto.CheckInRequest;
import com.ttcs.homestay.dto.CheckInResponse;
import com.ttcs.homestay.dto.MaintenanceRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.dto.RoomStatusHistoryResponse;
import com.ttcs.homestay.dto.UpdateRoomStatusRequest;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.service.RoomService;
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

/**
 * S1-10: trạng thái phòng. Quyền khai báo trong SecurityConfig (S1-04).
 * AC5: người thao tác lưu vào lịch sử lấy từ tài khoản đang đăng nhập (họ tên trong token).
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /** S1-04: Buồng phòng chỉ thấy phòng Trống bẩn (cần dọn). */
    @GetMapping
    public List<RoomResponse> getRooms(Authentication authentication) {
        List<RoomResponse> rooms = roomService.getRooms();
        if (isHousekeeping(authentication)) {
            return rooms.stream()
                    .filter(room -> room.status() == RoomStatus.TRONG_BAN)
                    .toList();
        }
        return rooms;
    }

    @GetMapping("/{roomId}/history")
    public List<RoomStatusHistoryResponse> getHistory(@PathVariable Long roomId) {
        return roomService.getHistory(roomId);
    }

    @PatchMapping("/{roomId}/status")
    public RoomResponse updateStatus(
            @PathVariable Long roomId,
            @Valid @RequestBody UpdateRoomStatusRequest request,
            Authentication authentication) {
        if (isHousekeeping(authentication)) {
            RoomResponse room = roomService.getRooms().stream()
                    .filter(candidate -> candidate.id().equals(roomId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "Không có quyền thao tác phòng này"));
            // S1-04: Buồng phòng chỉ được báo phòng Trống bẩn đã dọn xong (sang Trống sạch).
            if (room.status() != RoomStatus.TRONG_BAN || request.status() != RoomStatus.TRONG_SACH) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Housekeeping chỉ được chuyển phòng Trống bẩn sang Trống sạch");
            }
        }
        return roomService.updateStatus(roomId, request.status(), operatorName(authentication));
    }

    @PatchMapping("/{roomId}/maintenance")
    public RoomResponse putIntoMaintenance(
            @PathVariable Long roomId,
            @Valid @RequestBody MaintenanceRequest request,
            Authentication authentication) {
        return roomService.putIntoMaintenance(roomId, request, operatorName(authentication));
    }

    @PostMapping("/{roomId}/check-in")
    public ResponseEntity<CheckInResponse> checkIn(
            @PathVariable Long roomId,
            @Valid @RequestBody CheckInRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(roomService.checkIn(roomId, request, operatorName(authentication)));
    }

    @PostMapping("/{roomId}/check-out")
    public ResponseEntity<RoomResponse> checkOut(@PathVariable Long roomId, Authentication authentication) {
        return ResponseEntity.ok(roomService.checkOut(roomId, operatorName(authentication)));
    }

    private static boolean isHousekeeping(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_HOUSEKEEPING".equals(authority.getAuthority()));
    }

    /** AC5: họ tên người đang đăng nhập; token không có họ tên thì dùng email. */
    static String operatorName(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            String fullName = token.getToken().getClaimAsString("fullName");
            if (fullName != null && !fullName.isBlank()) {
                return fullName;
            }
            String email = token.getToken().getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        return authentication.getName();
    }
}