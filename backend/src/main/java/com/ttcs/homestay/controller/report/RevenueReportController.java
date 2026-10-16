package com.ttcs.homestay.controller.report;

import com.ttcs.homestay.dto.report.RevenueReportResponse;
import com.ttcs.homestay.service.RevenueReportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S4-07 Lát 1: thống kê doanh thu theo khoảng ngày. Quyền truy cập khai báo trong SecurityConfig. */
@RestController
@RequestMapping("/api/reports")
public class RevenueReportController {

    private final RevenueReportService revenueReportService;

    public RevenueReportController(RevenueReportService revenueReportService) {
        this.revenueReportService = revenueReportService;
    }

    @GetMapping("/revenue")
    public RevenueReportResponse revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return revenueReportService.getReport(startDate, endDate);
    }
}