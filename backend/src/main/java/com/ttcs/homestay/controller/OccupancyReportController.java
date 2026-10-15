package com.ttcs.homestay.controller;

import com.ttcs.homestay.dto.OccupancyReportResponse;
import com.ttcs.homestay.service.OccupancyReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports")
public class OccupancyReportController {
    private final OccupancyReportService service;
    public OccupancyReportController(OccupancyReportService service) { this.service = service; }

    @GetMapping("/occupancy")
    public OccupancyReportResponse report(@RequestParam String startDate, @RequestParam String endDate,
                                           @RequestParam(required = false) Long roomTypeId) {
        return getReport(startDate, endDate, roomTypeId);
    }

    @GetMapping(value = "/occupancy.csv", produces = "text/csv")
    public ResponseEntity<byte[]> downloadCsv(@RequestParam String startDate, @RequestParam String endDate,
                                               @RequestParam(required = false) Long roomTypeId) {
        OccupancyReportResponse report = getReport(startDate, endDate, roomTypeId);
        String filename = "bao-cao-cong-suat-" + report.startDate() + "-" + report.endDate() + ".csv";
        byte[] body = ("\uFEFF" + csv(report)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    private OccupancyReportResponse getReport(String start, String end, Long roomTypeId) {
        try { return service.report(LocalDate.parse(start), LocalDate.parse(end), roomTypeId); }
        catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception); }
    }

    private static String csv(OccupancyReportResponse report) {
        StringBuilder csv = new StringBuilder("Báo cáo công suất phòng\r\n");
        csv.append("Khoảng ngày;").append(report.startDate()).append(" - ").append(report.endDate()).append("\r\n");
        csv.append("Loại phòng;").append(escape(report.selectedRoomTypeName())).append("\r\n\r\n");
        csv.append("Ngày;Loại phòng;Đêm đã bán;Đêm khả dụng;Công suất\r\n");
        for (var day : report.dailyOccupancies()) {
            csv.append(day.date()).append(';').append(escape(report.selectedRoomTypeName())).append(';')
                    .append(day.soldNights()).append(';').append(day.availableNights()).append(';')
                    .append(formatPercent(day.occupancyPercent())).append("\r\n");
        }
        csv.append("Tổng kỳ;").append(escape(report.selectedRoomTypeName())).append(';')
                .append(report.totalSoldNights()).append(';').append(report.totalAvailableNights()).append(';')
                .append(formatPercent(report.totalAverageOccupancyPercent())).append("\r\n");
        return csv.toString();
    }

    private static String formatPercent(BigDecimal value) { return value == null ? "Không có phòng khả dụng" : value.toPlainString().replace('.', ',') + "%"; }
    private static String escape(String value) { String safe = value == null ? "" : value; return '"' + safe.replace("\"", "\"\"") + '"'; }
}
