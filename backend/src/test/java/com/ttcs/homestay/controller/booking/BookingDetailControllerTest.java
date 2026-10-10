
package com.ttcs.homestay.controller.booking;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.repository.BookingDepositAdjustmentRepository;
import com.ttcs.homestay.service.BookingDepositAdjustmentService;
import com.ttcs.homestay.entity.BookingDepositAdjustment;
import com.ttcs.homestay.entity.DepositAdjustmentType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingDetailController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private BookingDepositAdjustmentRepository adjustmentRepository;

    @MockitoBean
    private BookingDepositAdjustmentService adjustmentService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void returnsAdjustmentHistoryInRepositoryOrderAndCurrentTotal() throws Exception {
        when(jdbcTemplate.query(eq("SELECT id, booking_code, status, hold_expires_at FROM bookings WHERE id = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any(), eq(1L)))
                .thenReturn(List.of(Map.of("id", 1L, "bookingCode", "BK-1", "status", "DA_XAC_NHAN",
                        "holdExpiresAt", OffsetDateTime.parse("2026-10-08T10:00:00Z"))));
        when(jdbcTemplate.query(org.mockito.ArgumentMatchers.contains("FROM booking_deposits"),
                org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any(), eq(1L)))
                .thenReturn(List.of(Map.of("id", 10L, "amount", new BigDecimal("400.0000"))));
        BookingDepositAdjustment earlier = adjustment(2L, DepositAdjustmentType.TANG,
                "10.2500", "add", "A", "2026-01-01T00:00:00Z");
        BookingDepositAdjustment later = adjustment(3L, DepositAdjustmentType.GIAM,
                "5.1250", "refund", "B", "2026-01-01T00:00:00Z");
        when(adjustmentRepository.findAllByBookingIdOrderByCreatedAtAscIdAsc(1L))
                .thenReturn(List.of(earlier, later));
        when(adjustmentService.calculateCurrentDepositTotal(1L)).thenReturn(new BigDecimal("405.1250"));

        mockMvc.perform(get("/api/bookings/1/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value("BK-1"))
                .andExpect(jsonPath("$.deposit.id").value(10))
                .andExpect(jsonPath("$.depositAdjustments.length()").value(2))
                .andExpect(jsonPath("$.depositAdjustments[0].id").value(2))
                .andExpect(jsonPath("$.depositAdjustments[0].adjustmentType").value("TANG"))
                .andExpect(jsonPath("$.depositAdjustments[0].amount").value(10.25))
                .andExpect(jsonPath("$.depositAdjustments[0].reason").value("add"))
                .andExpect(jsonPath("$.depositAdjustments[0].createdBy").value("A"))
                .andExpect(jsonPath("$.depositAdjustments[1].id").value(3))
                .andExpect(jsonPath("$.currentDepositTotal").value(405.125));

        verify(adjustmentRepository).findAllByBookingIdOrderByCreatedAtAscIdAsc(1L);
        verify(adjustmentService).calculateCurrentDepositTotal(1L);
    }

    @Test
    void noAdjustmentsReturnsEmptyListAndOriginalDepositTotal() throws Exception {
        stubBookingAndDeposit(true);
        when(adjustmentRepository.findAllByBookingIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of());
        when(adjustmentService.calculateCurrentDepositTotal(1L)).thenReturn(new BigDecimal("400.0000"));

        mockMvc.perform(get("/api/bookings/1/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositAdjustments").isEmpty())
                .andExpect(jsonPath("$.currentDepositTotal").value(400));
    }

    @Test
    void missingOriginalDepositKeepsDetailsAvailableAndReturnsNullTotal() throws Exception {
        stubBookingAndDeposit(false);
        when(adjustmentRepository.findAllByBookingIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/1/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deposit").doesNotExist())
                .andExpect(jsonPath("$.depositAdjustments").isEmpty())
                .andExpect(jsonPath("$.currentDepositTotal").value(org.hamcrest.Matchers.nullValue()));
        verify(adjustmentService, never()).calculateCurrentDepositTotal(1L);
    }

    @Test
    void missingBookingReturnsNotFound() throws Exception {
        when(jdbcTemplate.query(eq("SELECT id, booking_code, status, hold_expires_at FROM bookings WHERE id = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any(), eq(1L))).thenReturn(List.of());
        mockMvc.perform(get("/api/bookings/1/details")).andExpect(status().isNotFound());
        verifyNoInteractions(adjustmentRepository, adjustmentService);
    }

    @Test
    void updateDepositIsRejected() throws Exception {
        mockMvc.perform(put("/api/bookings/1/deposit")
                .contentType("application/json")
                .content("{\"amount\":999999}"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void deleteDepositIsRejected() throws Exception {
        mockMvc.perform(delete("/api/bookings/1/deposit"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(jdbcTemplate);
    }

    private void stubBookingAndDeposit(boolean hasDeposit) {
        when(jdbcTemplate.query(eq("SELECT id, booking_code, status, hold_expires_at FROM bookings WHERE id = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any(), eq(1L)))
                .thenReturn(List.of(Map.of("id", 1L, "bookingCode", "BK-1", "status", "DA_XAC_NHAN",
                        "holdExpiresAt", OffsetDateTime.parse("2026-10-08T10:00:00Z"))));
        when(jdbcTemplate.query(org.mockito.ArgumentMatchers.contains("FROM booking_deposits"),
                org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any(), eq(1L)))
                .thenReturn(hasDeposit ? List.of(Map.of("id", 10L, "amount", new BigDecimal("400.0000"))) : List.of());
    }

    private BookingDepositAdjustment adjustment(Long id, DepositAdjustmentType type, String amount,
            String reason, String actor, String createdAt) {
        BookingDepositAdjustment adjustment = mock(BookingDepositAdjustment.class);
        when(adjustment.getId()).thenReturn(id);
        when(adjustment.getAdjustmentType()).thenReturn(type);
        when(adjustment.getAmount()).thenReturn(new BigDecimal(amount));
        when(adjustment.getReason()).thenReturn(reason);
        when(adjustment.getCreatedBy()).thenReturn(actor);
        when(adjustment.getCreatedAt()).thenReturn(OffsetDateTime.parse(createdAt));
        return adjustment;
    }
}
