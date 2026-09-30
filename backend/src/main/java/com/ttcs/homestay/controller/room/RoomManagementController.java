package com.ttcs.homestay.controller.room;

import com.ttcs.homestay.dto.CreatePhysicalRoomRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.dto.RoomSearchResponse;
import com.ttcs.homestay.dto.RoomUpdateResponse;
import com.ttcs.homestay.dto.UpdatePhysicalRoomRequest;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.service.RoomManagementService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public RoomManagementController(RoomManagementService roomManagementService) {
        this.roomManagementService = roomManagementService;
    }

    /** SCRUM-9: tạo phòng vật lý. */
    @PostMapping("/rooms")
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreatePhysicalRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomManagementService.create(request));
    }

    /** SCRUM-17: lọc kết hợp theo loại phòng, tầng, trạng thái. */
    @GetMapping("/rooms")
    public RoomSearchResponse searchRooms(
            @RequestParam(required = false) String roomType,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) RoomStatus status,
            @RequestParam(required = false) Boolean active) {
        return roomManagementService.search(roomType, floor, status, active);
    }

    /** SCRUM-12: cập nhật thông tin phòng; trả warning trước khi lưu nếu chưa kiểm tra được booking. */
    @PatchMapping("/rooms/{roomId}")
    public RoomUpdateResponse updateRoom(
            @PathVariable Long roomId,
            @Valid @RequestBody UpdatePhysicalRoomRequest request) {
        return roomManagementService.update(roomId, request);
    }

    @GetMapping("/rooms/{roomId}/note")
    public Map<String, String> getRoomNote(@PathVariable Long roomId) {
        return Map.of("note", roomManagementService.getNote(roomId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "ROOM_MANAGEMENT_ERROR",
                "message", exception.getMessage()));
    }
}
