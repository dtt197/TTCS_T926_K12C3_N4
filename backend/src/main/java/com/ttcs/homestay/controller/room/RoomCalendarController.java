package com.ttcs.homestay.controller.room;

import com.ttcs.homestay.dto.RoomCalendarResponse;
import com.ttcs.homestay.service.RoomCalendarService;
import java.time.LocalDate;
import java.time.DateTimeException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rooms/calendar")
public class RoomCalendarController {
    private final RoomCalendarService service;

    public RoomCalendarController(RoomCalendarService service) { this.service = service; }

    @GetMapping
    public RoomCalendarResponse getCalendar(@RequestParam String startDate,
            @RequestParam(defaultValue = "14") int days) {
        if (!startDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || days < 1 || days > 14) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày phải có dạng YYYY-MM-DD; số ngày từ 1 đến 14");
        }
        try {
            return service.getCalendar(LocalDate.parse(startDate), days);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày không hợp lệ", e);
        }
    }
}
