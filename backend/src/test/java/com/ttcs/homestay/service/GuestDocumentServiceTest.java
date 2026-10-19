package com.ttcs.homestay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.GuestDocumentResponse;
import com.ttcs.homestay.dto.booking.GuestIdentityResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingGuest;
import com.ttcs.homestay.repository.BookingGuestRepository;
import com.ttcs.homestay.repository.BookingRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Bảo vệ dữ liệu cá nhân: che số giấy tờ còn 4 ký tự cuối, xem đầy đủ thì ghi nhật ký (S1-05). */
class GuestDocumentServiceTest {

    private BookingRepository bookingRepository;
    private BookingGuestRepository guestRepository;
    private AuditLogService auditLogService;
    private GuestDocumentService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        guestRepository = mock(BookingGuestRepository.class);
        auditLogService = mock(AuditLogService.class);
        service = new GuestDocumentService(bookingRepository, guestRepository, auditLogService);
    }

    private BookingGuest guest(long id, long bookingId, int order, String name, String identity) {
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setBookingCode("BK" + bookingId);
        BookingGuest guest = new BookingGuest();
        guest.setId(id);
        guest.setBooking(booking);
        guest.setGuestOrder(order);
        guest.setFullName(name);
        guest.setIdentityNumber(identity);
        return guest;
    }

    @Test
    void cheSoGiayToChiConBonKyTuCuoi() {
        assertEquals("********7890", GuestDocumentService.mask("001204567890"));
        assertEquals("****", GuestDocumentService.mask("1234"));
        assertNull(GuestDocumentService.mask(null));
        assertNull(GuestDocumentService.mask("  "));
    }

    @Test
    void danhSachKhachChiTraSoDaChe() {
        when(bookingRepository.existsById(5L)).thenReturn(true);
        when(guestRepository.findAllByBookingIdOrderByGuestOrderAsc(5L)).thenReturn(List.of(
                guest(1L, 5L, 0, "Nguyễn Văn A", "001204567890"),
                guest(2L, 5L, 1, "Trần Thị B", null)));

        List<GuestDocumentResponse> result = service.listMasked(5L);

        assertEquals("********7890", result.get(0).maskedIdentityNumber());
        assertTrue(result.get(0).primary());
        assertTrue(result.get(0).hasIdentityNumber());
        assertFalse(result.get(1).hasIdentityNumber());
        assertFalse(result.toString().contains("001204567890"), "Không được trả số đầy đủ trong danh sách");
    }

    @Test
    void xemDayDuThiGhiNhatKy() {
        when(guestRepository.findById(1L)).thenReturn(Optional.of(guest(1L, 5L, 0, "Nguyễn Văn A", "001204567890")));

        GuestIdentityResponse result = service.revealIdentity(5L, 1L, 7L, "letan.demo@homestay.local", "10.0.0.8");

        assertEquals("001204567890", result.identityNumber());
        verify(auditLogService).recordSensitiveActionInCurrentTransaction(
                eq(7L), eq("letan.demo@homestay.local"), eq(null), eq("Booking BK5 - Nguyễn Văn A"),
                eq("GUEST_DOCUMENT_VIEWED"), eq("10.0.0.8"));
    }

    @Test
    void khachKhongThuocBookingThi404VaKhongGhiNhatKy() {
        when(guestRepository.findById(1L)).thenReturn(Optional.of(guest(1L, 9L, 0, "Nguyễn Văn A", "001204567890")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.revealIdentity(5L, 1L, 7L, "letan.demo@homestay.local", "10.0.0.8"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(auditLogService, never()).recordSensitiveActionInCurrentTransaction(
                any(), any(), any(), anyString(), anyString(), any());
    }
}
