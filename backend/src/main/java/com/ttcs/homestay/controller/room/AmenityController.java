package com.ttcs.homestay.controller.room;

import com.ttcs.homestay.dto.amenity.AmenityRequest;
import com.ttcs.homestay.dto.amenity.AmenityResponse;
import com.ttcs.homestay.dto.amenity.UpdateAmenityStatusRequest;
import com.ttcs.homestay.service.AmenityService;
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
 * S1-08: API tiện nghi. Quyền khai báo trong SecurityConfig:
 * xem cho 4 vai trò nội bộ, thêm/sửa/ngừng dùng/xoá cho Chủ homestay và Quản trị.
 */
@RestController
@RequestMapping("/api/amenities")
public class AmenityController {

    private final AmenityService amenityService;

    public AmenityController(AmenityService amenityService) {
        this.amenityService = amenityService;
    }

    @GetMapping
    public List<AmenityResponse> listAmenities() {
        return amenityService.listAmenities();
    }

    @PostMapping
    public ResponseEntity<AmenityResponse> createAmenity(@Valid @RequestBody AmenityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(amenityService.createAmenity(request));
    }

    @PutMapping("/{id}")
    public AmenityResponse updateAmenity(@PathVariable Long id, @Valid @RequestBody AmenityRequest request) {
        return amenityService.updateAmenity(id, request);
    }

    @PatchMapping("/{id}/status")
    public AmenityResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateAmenityStatusRequest request) {
        return amenityService.updateStatus(id, request.active());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAmenity(@PathVariable Long id) {
        amenityService.deleteAmenity(id);
        return ResponseEntity.noContent().build();
    }
}