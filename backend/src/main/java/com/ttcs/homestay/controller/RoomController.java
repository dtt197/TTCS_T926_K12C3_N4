package com.ttcs.homestay.controller;

import com.ttcs.homestay.dto.CheckInRequest;
import com.ttcs.homestay.dto.CheckInResponse;
import com.ttcs.homestay.dto.MaintenanceRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.dto.RoomStatusHistoryResponse;
import com.ttcs.homestay.dto.UpdateRoomStatusRequest;
import com.ttcs.homestay.service.RoomService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
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
            @RequestHeader(value = "X-Operator-Name", defaultValue = "Lễ tân") String operatorName
    ) {
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
}