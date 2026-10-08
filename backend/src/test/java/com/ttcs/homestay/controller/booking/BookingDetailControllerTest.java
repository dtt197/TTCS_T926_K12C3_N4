
package com.ttcs.homestay.controller.booking;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private UserRepository userRepository;

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
}