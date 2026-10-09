package com.ttcs.homestay.controller.booking;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.service.BookingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingAvailableRoomsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private com.ttcs.homestay.service.RoomShortageAlertService roomShortageAlertService;

    @MockitoBean
    private com.ttcs.homestay.service.BookingDepositAdjustmentService bookingDepositAdjustmentService;

    @Test
    void getAvailableRoomsUsesNumericBookingIdAndReturnsRoomList() throws Exception {
        when(bookingService.getAvailableRoomsForBooking(63L)).thenReturn(List.of(
                new RoomResponse(105L, "105", 1, "Phòng đôi", RoomStatus.TRONG_SACH, true)));

        mockMvc.perform(get("/api/bookings/63/available-rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(105))
                .andExpect(jsonPath("$[0].roomNumber").value("105"));

        verify(bookingService).getAvailableRoomsForBooking(63L);
    }
}
