package com.ttcs.homestay.controller.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ttcs.homestay.entity.BookingDepositAdjustment;
import com.ttcs.homestay.repository.BookingDepositAdjustmentRepository;
import com.ttcs.homestay.service.BookingDepositAdjustmentService;
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
    private final BookingDepositAdjustmentRepository adjustmentRepository;
    private final BookingDepositAdjustmentService adjustmentService;

    public BookingDetailController(
            JdbcTemplate jdbcTemplate,
            BookingDepositAdjustmentRepository adjustmentRepository,
            BookingDepositAdjustmentService adjustmentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.adjustmentRepository = adjustmentRepository;
        this.adjustmentService = adjustmentService;
    }

    @GetMapping("/{id}/details")
    public Map<String, Object> getDetails(@PathVariable Long id) {
        List<Map<String, Object>> bookings = jdbcTemplate.query(
                "SELECT id, booking_code, status, hold_expires_at FROM bookings WHERE id = ?",
                (rs, rowNum) -> bookingRow(rs), id);
        if (bookings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        Map<String, Object> result = new LinkedHashMap<>(bookings.get(0));
        List<Map<String, Object>> registeredGuests = jdbcTemplate.query(
                "SELECT full_name, guest_order FROM booking_guests " +
                        "WHERE booking_id = ? ORDER BY guest_order",
                (rs, rowNum) -> {
                    Map<String, Object> guest = new LinkedHashMap<>();
                    guest.put("fullName", rs.getString("full_name"));
                    guest.put("primary", rs.getInt("guest_order") == 0);
                    return guest;
                }, id);
        result.put("registeredGuests", registeredGuests);

        List<Map<String, Object>> deposits = jdbcTemplate.query(
                "SELECT id, amount, payment_method, received_date, payment_reference, created_by, created_at " +
                        "FROM booking_deposits WHERE booking_id = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> depositRow(rs), id);
        result.put("deposit", deposits.isEmpty() ? null : deposits.get(0));

        List<Map<String, Object>> histories = jdbcTemplate.query(
                "SELECT id, booking_id, booking_code, old_check_in_date, new_check_in_date, " +
                        "old_check_out_date, new_check_out_date, old_room_type_id, old_room_type_name, " +
                        "new_room_type_id, new_room_type_name, old_total_amount, new_total_amount, " +
                        "actor_user_id, actor_name, actor_email, created_at " +
                        "FROM booking_audit_logs WHERE booking_id = ? ORDER BY created_at DESC, id DESC",
                (rs, rowNum) -> historyRow(rs), id);
        result.put("history", histories);
        List<BookingDepositAdjustment> adjustments = adjustmentRepository
                .findAllByBookingIdOrderByCreatedAtAscIdAsc(id);
        result.put("depositAdjustments", adjustments.stream()
                .map(BookingDetailController::adjustmentRow).toList());
        result.put("currentDepositTotal", deposits.isEmpty()
                ? null
                : adjustmentService.calculateCurrentDepositTotal(id));
        return result;
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/deposit")
    public void rejectDepositUpdate(@PathVariable Long id) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Tiền cọc đã ghi nhận chỉ được xem, không thể cập nhật hoặc xóa");
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/deposit")
    public void rejectDepositDelete(@PathVariable Long id) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Tiền cọc đã ghi nhận chỉ được xem, không thể cập nhật hoặc xóa");
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

    private static Map<String, Object> historyRow(ResultSet rs) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", rs.getLong("id"));
        result.put("bookingId", rs.getLong("booking_id"));
        result.put("bookingCode", rs.getString("booking_code"));
        result.put("oldCheckInDate", rs.getObject("old_check_in_date", java.time.LocalDate.class));
        result.put("newCheckInDate", rs.getObject("new_check_in_date", java.time.LocalDate.class));
        result.put("oldCheckOutDate", rs.getObject("old_check_out_date", java.time.LocalDate.class));
        result.put("newCheckOutDate", rs.getObject("new_check_out_date", java.time.LocalDate.class));
        result.put("oldRoomTypeId", rs.getObject("old_room_type_id", Long.class));
        result.put("oldRoomTypeName", rs.getString("old_room_type_name"));
        result.put("newRoomTypeId", rs.getObject("new_room_type_id", Long.class));
        result.put("newRoomTypeName", rs.getString("new_room_type_name"));
        result.put("oldTotalAmount", rs.getLong("old_total_amount"));
        result.put("newTotalAmount", rs.getLong("new_total_amount"));
        result.put("actorUserId", rs.getObject("actor_user_id", Long.class));
        result.put("actorName", rs.getString("actor_name"));
        result.put("actorEmail", rs.getString("actor_email"));
        result.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
        return result;
    }

    private static Map<String, Object> adjustmentRow(BookingDepositAdjustment adjustment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", adjustment.getId());
        result.put("adjustmentType", adjustment.getAdjustmentType());
        result.put("amount", adjustment.getAmount());
        result.put("reason", adjustment.getReason());
        result.put("createdBy", adjustment.getCreatedBy());
        result.put("createdAt", adjustment.getCreatedAt());
        return result;
    }
}
