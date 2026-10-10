package com.ttcs.homestay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.ttcs.homestay.config.SecurityConfig;
import com.ttcs.homestay.controller.room.RoomCalendarController;
import com.ttcs.homestay.entity.*;
import com.ttcs.homestay.repository.*;
import com.ttcs.homestay.service.RoomCalendarService;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// MVC slice only: actual controller, service and security; no JPA or datasource autoconfiguration.
@WebMvcTest(RoomCalendarController.class)
@Import({SecurityConfig.class, RoomCalendarService.class, RoomCalendarControllerTest.TestSecurity.class})
class RoomCalendarControllerTest {
    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurity {}

    @Autowired MockMvc mvc;
    @Autowired WebApplicationContext webContext;
    @Autowired ApplicationContext context;
    @MockitoBean RoomRepository rooms;
    @MockitoBean BookingRepository bookings;
    @MockitoBean UserRepository users;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach void configureSecurityFilters() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
    }

    @Test void noDatasourceExists() {
        assertThat(context.getBeansOfType(javax.sql.DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class)).isEmpty();
    }

    @Test void anonymousDenied() throws Exception {
        mvc.perform(get("/api/rooms/calendar?startDate=2027-06-10")).andExpect(status().isUnauthorized());
        verifyNoInteractions(rooms, bookings);
    }

    @ParameterizedTest @ValueSource(strings = {"HOUSEKEEPING", "ADMIN", "OWNER"})
    void otherRolesDenied(String role) throws Exception {
        mvc.perform(get("/api/rooms/calendar?startDate=2027-06-10").with(user("staff").roles(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rooms, bookings);
    }

    @ParameterizedTest @ValueSource(ints = {1, 14})
    void receptionistCanReadValidRanges(int days) throws Exception {
        when(rooms.findAllByActiveTrueOrderByRoomNumberAsc()).thenReturn(List.of());
        mvc.perform(get("/api/rooms/calendar").param("startDate", "2027-06-10")
                .param("days", Integer.toString(days)).with(user("staff").roles("RECEPTIONIST")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dates.length()").value(days))
                .andExpect(jsonPath("$.startDate").value("2027-06-10"))
                .andExpect(jsonPath("$.endDateExclusive").value(LocalDate.of(2027, 6, 10).plusDays(days).toString()))
                .andExpect(jsonPath("$.rooms").isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "15", "abc"})
    void invalidDaysRejected(String days) throws Exception {
        mvc.perform(get("/api/rooms/calendar").param("startDate", "2027-06-10").param("days", days)
                .with(user("staff").roles("RECEPTIONIST"))).andExpect(status().isBadRequest());
        verifyNoInteractions(rooms, bookings);
    }

    @ParameterizedTest @ValueSource(strings = {"2027-6-10", "10/06/2027", "2027-02-29", "2027-13-01", ""})
    void invalidDatesRejected(String date) throws Exception {
        mvc.perform(get("/api/rooms/calendar").param("startDate", date)
                .with(user("staff").roles("RECEPTIONIST"))).andExpect(status().isBadRequest());
        verifyNoInteractions(rooms, bookings);
    }

    @Test void missingDateRejected() throws Exception {
        mvc.perform(get("/api/rooms/calendar").with(user("staff").roles("RECEPTIONIST")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rooms, bookings);
    }

    @Test void getReturnsCellsWithoutMutationsOrRepositoryWrites() throws Exception {
        Room room = new Room(); room.setId(1L); room.setRoomNumber("101"); room.setRoomType("Double");
        room.setStatus(RoomStatus.TRONG_SACH);
        Booking booking = new Booking(); booking.setRoom(room); booking.setStatus(BookingStatus.CHO_XAC_NHAN);
        booking.setCheckInDate(LocalDate.of(2027, 6, 10)); booking.setCheckOutDate(LocalDate.of(2027, 6, 11));
        OffsetDateTime expiry = OffsetDateTime.now().minusDays(1); booking.setHoldExpiresAt(expiry);
        when(rooms.findAllByActiveTrueOrderByRoomNumberAsc()).thenReturn(List.of(room));
        when(bookings.findOverlappingOnRooms(anyList(), any(), any(), any())).thenReturn(List.of(booking));
        mvc.perform(get("/api/rooms/calendar?startDate=2027-06-10").with(user("staff").roles("RECEPTIONIST")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dates.length()").value(14))
                .andExpect(jsonPath("$.rooms[0].roomId").value(1))
                .andExpect(jsonPath("$.rooms[0].roomNumber").value("101"))
                .andExpect(jsonPath("$.rooms[0].cells.length()").value(14))
                .andExpect(jsonPath("$.rooms[0].cells[0].date").value("2027-06-10"))
                .andExpect(jsonPath("$.rooms[0].cells[0].status").value("AVAILABLE"));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHO_XAC_NHAN);
        assertThat(booking.getHoldExpiresAt()).isEqualTo(expiry);
        assertThat(booking.getRoom()).isSameAs(room);
        verify(rooms).findAllByActiveTrueOrderByRoomNumberAsc();
        verify(bookings).findOverlappingOnRooms(eq(List.of(room)), eq(LocalDate.of(2027, 6, 10)),
                eq(LocalDate.of(2027, 6, 24)), any());
        verifyNoMoreInteractions(rooms, bookings);
    }
}
