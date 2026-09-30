package com.ttcs.homestay.controller.room;

import com.ttcs.homestay.dto.roomtype.RoomTypeRequest;
import com.ttcs.homestay.dto.roomtype.RoomTypeResponse;
import com.ttcs.homestay.dto.roomtype.UpdateRoomTypeStatusRequest;
import com.ttcs.homestay.service.RoomTypeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S1-06: API loại phòng. Quyền khai báo trong SecurityConfig:
 * xem cho 4 vai trò nội bộ, thêm/sửa/ngừng bán/xoá cho Chủ homestay và Quản trị.
 */
@RestController
@RequestMapping("/api/room-types")
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    public RoomTypeController(RoomTypeService roomTypeService) {
        this.roomTypeService = roomTypeService;
    }

    @GetMapping
    public List<RoomTypeResponse> listRoomTypes() {
        return roomTypeService.listRoomTypes();
    }

    @PostMapping
    public ResponseEntity<RoomTypeResponse> createRoomType(@Valid @RequestBody RoomTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomTypeService.createRoomType(request));
    }

    @PutMapping("/{id}")
    public RoomTypeResponse updateRoomType(@PathVariable Long id, @Valid @RequestBody RoomTypeRequest request) {
        return roomTypeService.updateRoomType(id, request);
    }

    @PatchMapping("/{id}/status")
    public RoomTypeResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateRoomTypeStatusRequest request) {
        return roomTypeService.updateStatus(id, request.active());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoomType(@PathVariable Long id) {
        roomTypeService.deleteRoomType(id);
        return ResponseEntity.noContent().build();
    }
    
    /** S1-08 AC2: gắn một tiện nghi cho loại phòng (gắn trùng bị chặn, trả 409). */
    @PostMapping("/{id}/amenities/{amenityId}")
    public RoomTypeResponse addAmenity(@PathVariable Long id, @PathVariable Long amenityId) {
        return roomTypeService.addAmenity(id, amenityId);
    }

    /** S1-08: bỏ một tiện nghi khỏi loại phòng. */
    @DeleteMapping("/{id}/amenities/{amenityId}")
    public RoomTypeResponse removeAmenity(@PathVariable Long id, @PathVariable Long amenityId) {
        return roomTypeService.removeAmenity(id, amenityId);
    }
}