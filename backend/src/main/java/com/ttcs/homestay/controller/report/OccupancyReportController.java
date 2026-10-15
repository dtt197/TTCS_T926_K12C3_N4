package com.ttcs.homestay.controller.report;

import com.ttcs.homestay.dto.OccupancyReportResponse;
import com.ttcs.homestay.service.OccupancyReportService;
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
public class OccupancyReportController {
    private final OccupancyReportService service;

    public OccupancyReportController(OccupancyReportService service) { this.service = service; }

    @GetMapping
    public OccupancyReportResponse report(@RequestParam String startDate, @RequestParam String endDate) {
        try {
            return service.report(LocalDate.parse(startDate), LocalDate.parse(endDate));
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
