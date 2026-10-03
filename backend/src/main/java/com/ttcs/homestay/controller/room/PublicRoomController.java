package com.ttcs.homestay.controller.room;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.RoomTypeRepository;
import com.ttcs.homestay.service.RoomAvailabilityService;

@RestController
@RequestMapping("/api/public/rooms")
public class PublicRoomController {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailabilityService roomAvailabilityService;

    public PublicRoomController(RoomTypeRepository roomTypeRepository, RoomAvailabilityService roomAvailabilityService) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomAvailabilityService = roomAvailabilityService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchAvailableRooms(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam int guestCount) {

        // 1. Lấy tất cả loại phòng và lọc theo sức chứa (capacity >= guestCount)
        List<RoomType> validRoomTypes = roomTypeRepository.findAll().stream()
                .filter(rt -> rt.getMaxCapacity() >= guestCount)
                .toList();

        // 2. Tính số phòng trống cho từng loại phòng và loại bỏ loại phòng không còn phòng trống
        List<RoomAvailabilityResponse> result = validRoomTypes.stream()
                .map(rt -> {
                    int availableCount = roomAvailabilityService.availableRooms(rt, checkIn, checkOut);
                    return new RoomAvailabilityResponse(
                            rt.getId(),
                            rt.getName(),
                        rt.getMaxCapacity(),
                            rt.getWeekdayPrice(),
                            availableCount
                    );
                })
                .filter(res -> res.availableRooms() > 0)
                .toList();

        return ResponseEntity.ok(result);
    }

    // Response DTO record nội bộ hoặc tạo file riêng
    public record RoomAvailabilityResponse(
            Long roomTypeId,
            String name,
            int capacity,
            double price,
            int availableRooms
    ) {}
}