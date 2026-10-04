package com.ttcs.homestay.controller.booking;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.exception.GuestBookingExceptionHandler;
import com.ttcs.homestay.exception.RoomTypeNotFoundException;
import com.ttcs.homestay.service.GuestBookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicBookingControllerTest {

    private GuestBookingService guestBookingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        guestBookingService = org.mockito.Mockito.mock(GuestBookingService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PublicBookingController(guestBookingService))
                .setControllerAdvice(new GuestBookingExceptionHandler())
                .build();
    }

    @Test
    void missingRoomTypeReturns404WithFriendlyMessage() throws Exception {
        when(guestBookingService.getRoomTypeDetail(999L)).thenThrow(
                new RoomTypeNotFoundException("Không tìm thấy thông tin loại phòng #999"));

        mockMvc.perform(get("/api/public/room-types/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy thông tin loại phòng #999"));
    }

    @Test
    void discontinuedRoomTypeReturns404WithNotFoundMessage() throws Exception {
        when(guestBookingService.getRoomTypeDetail(1L)).thenThrow(
                new RoomTypeNotFoundException("Không tìm thấy thông tin loại phòng #1"));

        mockMvc.perform(get("/api/public/room-types/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy thông tin loại phòng #1"));
    }
}
