package com.ttcs.homestay.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ttcs.homestay.dto.CheckInRequest;
import com.ttcs.homestay.dto.CheckInResponse;
import com.ttcs.homestay.dto.MaintenanceRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.dto.RoomStatusHistoryResponse;
import com.ttcs.homestay.dto.UpdateRoomStatusRequest;
import com.ttcs.homestay.service.RoomService;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }
  @GetMapping
public List<RoomResponse> getRooms(Authentication authentication) {
    List<RoomResponse> rooms = roomService.getRooms();

    if (authentication.getAuthorities().stream()
            .anyMatch(authority ->
                    authority.getAuthority().equals("ROLE_HOUSEKEEPING"))) {
        return rooms.stream()
                .filter(room -> room.status() == com.ttcs.homestay.entity.RoomStatus.TRONG_BAN)
                .toList();
    }

    return rooms;
}
    @GetMapping("/{roomId}/history")
    public List<RoomStatusHistoryResponse> getHistory(
            @PathVariable Long roomId
    ) {
        return roomService.getHistory(roomId);
    }

   @PatchMapping("/{roomId}/status")
public RoomResponse updateStatus(
        @PathVariable Long roomId,
        @Valid @RequestBody UpdateRoomStatusRequest request,
        @RequestHeader(value = "X-Operator-Name", defaultValue = "Lễ tân") String operatorName,
        Authentication authentication
) {
    boolean isHousekeeping = authentication.getAuthorities().stream()
            .anyMatch(authority ->
                    "ROLE_HOUSEKEEPING".equals(authority.getAuthority()));

    if (isHousekeeping) {
        RoomResponse room = roomService.getRooms().stream()
                .filter(r -> r.id().equals(roomId))
                .findFirst()
                .orElseThrow(() ->
                        new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.FORBIDDEN,
                                "Không có quyền thao tác phòng này"));

       if (room.status() != com.ttcs.homestay.entity.RoomStatus.TRONG_BAN
        || request.status() != com.ttcs.homestay.entity.RoomStatus.TRONG_SACH) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Housekeeping chỉ được chuyển phòng Trống bẩn sang Trống sạch");
        }
    }

    return roomService.updateStatus(roomId, request.status(), operatorName);
}

    @PatchMapping("/{roomId}/maintenance")
    public RoomResponse putIntoMaintenance(
            @PathVariable Long roomId,
            @Valid @RequestBody MaintenanceRequest request,
            @RequestHeader(value = "X-Operator-Name", defaultValue = "Lễ tân") String operatorName
    ) {
        return roomService.putIntoMaintenance(roomId, request, operatorName);
    }

    @PostMapping("/{roomId}/check-in")
    public ResponseEntity<CheckInResponse> checkIn(
            @PathVariable Long roomId,
            @Valid @RequestBody CheckInRequest request,
            @RequestHeader(value = "X-Operator-Name", defaultValue = "Lễ tân") String operatorName
    ) {
        return ResponseEntity.ok(roomService.checkIn(roomId, request, operatorName));
    }

    @PostMapping("/{roomId}/check-out")
    public ResponseEntity<RoomResponse> checkOut(
            @PathVariable Long roomId,
            @RequestHeader(value = "X-Operator-Name", defaultValue = "Lễ tân") String operatorName
    ) {
        return ResponseEntity.ok(roomService.checkOut(roomId, operatorName));
    }
}