package com.ttcs.homestay.controller.room;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.RoomTypeRepository;
import com.ttcs.homestay.service.RoomAvailabilityService;

@RestController
@RequestMapping("/api/public/rooms")
public class PublicRoomController {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailabilityService roomAvailabilityService;

    public PublicRoomController(
            RoomTypeRepository roomTypeRepository,
            RoomAvailabilityService roomAvailabilityService
    ) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomAvailabilityService = roomAvailabilityService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<RoomAvailabilityResponse>> searchAvailableRooms(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam int guestCount) {
        if (guestCount < 1) {
            throw badRequest("Số lượng khách phải lớn hơn hoặc bằng 1.");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw badRequest("Ngày nhận phòng không được trước ngày hiện tại.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw badRequest("Ngày trả phòng phải sau ngày nhận phòng ít nhất một đêm.");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > 30) {
            throw badRequest("Khoảng thời gian tra cứu không được vượt quá 30 đêm.");
        }

        List<RoomType> validRoomTypes = roomTypeRepository.findAll().stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getStatus()))
                .filter(rt -> rt.getWeekdayPrice() != null && rt.getWeekdayPrice() > 0)
                .filter(rt -> rt.getMaxCapacity() >= guestCount)
                .toList();

        List<RoomAvailabilityResponse> result = validRoomTypes.stream()
                .map(rt -> {
                    int availableCount = roomAvailabilityService.availableRooms(rt, checkIn, checkOut);
                    return new RoomAvailabilityResponse(
                            rt.getId(),
                            rt.getName(),
                            rt.getMaxCapacity(),
                            rt.getWeekdayPrice() != null ? rt.getWeekdayPrice().doubleValue() : 0.0,
                            availableCount
                    );
                })
                .filter(res -> res.availableRooms() > 0)
                .toList();

        return ResponseEntity.ok(result);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record RoomAvailabilityResponse(
            Long roomTypeId,
            String name,
            int capacity,
            double price,
            int availableRooms
    ) {}
}