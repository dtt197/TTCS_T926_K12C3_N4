package com.ttcs.homestay.controller.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
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

/** Read-only booking details, including the deposit persisted for S3-01. */
@RestController
@RequestMapping("/api/bookings")
public class BookingDetailController {
    private final JdbcTemplate jdbcTemplate;

    public BookingDetailController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{id}/details")
    public Map<String, Object> getDetails(@PathVariable Long id) {
        List<Map<String, Object>> bookings = jdbcTemplate.query(
                "SELECT id, booking_code, status, hold_expires_at FROM bookings WHERE id = ?",
                (rs, rowNum) -> bookingRow(rs), id);
        if (bookings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        Map<String, Object> result = bookings.get(0);
        List<Map<String, Object>> deposits = jdbcTemplate.query(
                "SELECT id, amount, payment_method, received_date, payment_reference, created_by, created_at " +
                        "FROM booking_deposits WHERE booking_id = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> depositRow(rs), id);
        result.put("deposit", deposits.isEmpty() ? null : deposits.get(0));
        return result;
    }

    private static Map<String, Object> bookingRow(ResultSet rs) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", rs.getLong("id"));
        result.put("bookingCode", rs.getString("booking_code"));
        result.put("status", rs.getString("status"));
        var hold = rs.getObject("hold_expires_at", java.time.OffsetDateTime.class);
        result.put("holdExpiresAt", hold);
        return result;
    }

    private static Map<String, Object> depositRow(ResultSet rs) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", rs.getLong("id"));
        result.put("amount", rs.getBigDecimal("amount"));
        result.put("paymentMethod", rs.getString("payment_method"));
        result.put("receivedDate", rs.getObject("received_date", java.time.LocalDate.class));
        result.put("paymentReference", rs.getString("payment_reference"));
        result.put("createdBy", rs.getString("created_by"));
        result.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
        return result;
    }
}
