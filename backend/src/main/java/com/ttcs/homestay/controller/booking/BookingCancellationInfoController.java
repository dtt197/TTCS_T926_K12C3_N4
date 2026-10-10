package com.ttcs.homestay.controller.booking;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** S3-05 Lát 2: thông tin huỷ (lý do, người huỷ, thời điểm huỷ) của một booking, chỉ đọc. */
@RestController
@RequestMapping("/api/bookings")
public class BookingCancellationInfoController {

    private final JdbcTemplate jdbcTemplate;

    public BookingCancellationInfoController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{id}/cancellation-info")
    public Map<String, Object> getCancellationInfo(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT status, cancel_reason, cancelled_by, cancelled_at FROM bookings WHERE id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("status", rs.getString("status"));
                    row.put("cancelReason", rs.getString("cancel_reason"));
                    row.put("cancelledBy", rs.getString("cancelled_by"));
                    row.put("cancelledAt", rs.getObject("cancelled_at", OffsetDateTime.class));
                    return row;
                },
                id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking");
        }
        return rows.get(0);
    }
}