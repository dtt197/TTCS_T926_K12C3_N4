package com.ttcs.homestay.controller.report;

import com.ttcs.homestay.dto.OccupancyByRoomTypeResponse;
import com.ttcs.homestay.service.OccupancyByRoomTypeService;
import java.time.DateTimeException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports/occupancy")
public class OccupancyByRoomTypeController {
    private final OccupancyByRoomTypeService service;
    public OccupancyByRoomTypeController(OccupancyByRoomTypeService service) { this.service = service; }

    @GetMapping
    public OccupancyByRoomTypeResponse report(@RequestParam String startDate, @RequestParam String endDate,
            @RequestParam(required = false) Long roomTypeId) {
        try { return service.report(LocalDate.parse(startDate), LocalDate.parse(endDate), roomTypeId); }
        catch (DateTimeException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
