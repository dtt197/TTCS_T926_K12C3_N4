package com.ttcs.homestay.controller.report;

import com.ttcs.homestay.dto.report.RevenueReportResponse;
import com.ttcs.homestay.dto.report.RevenueByRoomTypeResponse;
import com.ttcs.homestay.dto.report.RevenueComparisonResponse;
import com.ttcs.homestay.dto.report.RevenueByRoomTypeComparisonResponse;
import com.ttcs.homestay.service.RevenueReportService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;

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

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @AuthenticationPrincipal Jwt jwt) {

        validateRevenueDateRange(startDate, endDate, jwt);

        return revenueReportService.getReport(startDate, endDate);
    }

    /**
     * S4-07 Lát 2:
     * GET /api/reports/revenue/by-room-type
     *
     * Có thể truyền roomTypeId để lọc một loại phòng.
     * Bỏ roomTypeId để lấy tất cả loại phòng.
     */
    @GetMapping("/revenue/by-room-type")
    public List<RevenueByRoomTypeResponse> revenueByRoomType(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @RequestParam(required = false) Long roomTypeId,

            @AuthenticationPrincipal Jwt jwt) {

        validateRevenueDateRange(startDate, endDate, jwt);

        return revenueReportService.getReportByRoomType(
                startDate,
                endDate,
                roomTypeId);
    }
    
    @GetMapping("/comparison")
    public RevenueComparisonResponse getComparison(
            @RequestParam("startDate") LocalDate startDate,
            @RequestParam("endDate") LocalDate endDate) {

        return revenueReportService.getComparison(
                startDate,
                endDate);
    }
    
    /**
     * S4-07 Lát 3:
     * So sánh doanh thu theo từng loại phòng.
     */
    @GetMapping("/revenue/by-room-type/comparison")
    public List<RevenueByRoomTypeComparisonResponse> getComparisonByRoomType(
            @RequestParam("startDate") LocalDate startDate,
            @RequestParam("endDate") LocalDate endDate,
            @RequestParam(value = "roomTypeId", required = false) Long roomTypeId) {

        return revenueReportService.getComparisonByRoomType(
                startDate,
                endDate,
                roomTypeId);
    }

    private void validateRevenueDateRange(
            LocalDate startDate,
            LocalDate endDate,
            Jwt jwt) {

        String role = jwt.getClaimAsString("role");

        if ("RECEPTIONIST".equals(role)) {
            LocalDate today = LocalDate.now(
                    ZoneId.of("Asia/Ho_Chi_Minh"));

            if (!today.equals(startDate)
                    || !today.equals(endDate)) {
                throw new AccessDeniedException(
                        "Lễ tân chỉ được xem doanh thu của ngày hiện tại.");
            }
        }
    }
}