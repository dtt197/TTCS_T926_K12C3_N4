package com.ttcs.homestay.controller.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.dto.booking.BookingConfirmResponse;
import com.ttcs.homestay.dto.booking.BookingDepositResponse;
import com.ttcs.homestay.exception.InvalidDepositException;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.service.BookingDepositAdjustmentService;
import com.ttcs.homestay.service.BookingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingConfirmControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private BookingService bookingService;
    @MockitoBean private BookingDepositAdjustmentService bookingDepositAdjustmentService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private com.ttcs.homestay.service.RoomShortageAlertService roomShortageAlertService;

    private BookingConfirmResponse response(String method, String reference, BigDecimal amount) {
        return new BookingConfirmResponse(1L, "BK-123456", "DA_XAC_NHAN", null,
                new BookingDepositResponse(100L, amount, method, LocalDate.of(2026, 10, 8),
                        reference, null, "Receptionist", OffsetDateTime.now()));
    }

    private String body(String amount, String method, String reference) {
        return "{\"amount\":" + amount + ",\"paymentMethod\":" +
                (method == null ? "null" : "\"" + method + "\"") +
                ",\"receivedDate\":\"2026-10-08\",\"paymentReference\":" +
                (reference == null ? "null" : "\"" + reference + "\"") +
                "}";
    }

    @Test
    void confirmCashSuccess() throws Exception {
        when(bookingService.confirmBooking(eq(1L), any())).thenReturn(response("CASH", null, BigDecimal.valueOf(500000)));
        mockMvc.perform(put("/api/bookings/1/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(body("500000", "CASH", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DA_XAC_NHAN"))
                .andExpect(jsonPath("$.deposit.amount").value(500000));
    }

    @Test
    void confirmTransferSuccess() throws Exception {
        when(bookingService.confirmBooking(eq(1L), any())).thenReturn(response("BANK_TRANSFER", "987654321", BigDecimal.valueOf(1000000)));
        mockMvc.perform(put("/api/bookings/1/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(body("1000000", "BANK_TRANSFER", "987654321")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deposit.paymentMethod").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.deposit.paymentReference").value("987654321"));
    }

    @Test
    void transferRequiresReference() throws Exception {
        when(bookingService.confirmBooking(eq(1L), any())).thenThrow(new InvalidDepositException("Reference required"));
        mockMvc.perform(put("/api/bookings/1/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(body("500000", "BANK_TRANSFER", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingMethodRejected() throws Exception {
        when(bookingService.confirmBooking(eq(1L), any())).thenThrow(new InvalidDepositException("Method required"));
        mockMvc.perform(put("/api/bookings/1/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(body("500000", null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingAmountRejected() throws Exception {
        when(bookingService.confirmBooking(eq(1L), any())).thenThrow(new InvalidDepositException("Amount required"));
        mockMvc.perform(put("/api/bookings/1/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(body("null", "CASH", null)))
                .andExpect(status().isBadRequest());
    }
}
