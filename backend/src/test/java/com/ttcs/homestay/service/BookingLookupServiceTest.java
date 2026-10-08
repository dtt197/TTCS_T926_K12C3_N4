package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingLookupResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.exception.BookingLookupNotFoundException;
import com.ttcs.homestay.repository.BookingRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** S2-08 Lát 1: tra cứu booking bằng mã và email. */
class BookingLookupServiceTest {

    private BookingRepository bookingRepository;
    private BookingLookupService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        service = new BookingLookupService(bookingRepository, new BookingLookupRateLimiter(), null,
                mock(com.ttcs.homestay.repository.BookingDepositRepository.class));
        when(bookingRepository.findByBookingCode("ABCD2345"))
                .thenReturn(Optional.of(booking(BookingStatus.CHO_XAC_NHAN)));
    }

    private static Booking booking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setBookingCode("ABCD2345");
        booking.setGuestName("Nguyễn Văn A");
        booking.setGuestEmail("khach@example.com");
        booking.setGuestPhone("0912345678");
        booking.setNote("Ghi chú của khách");
        booking.setStatus(status);
        booking.setRoomTypeNameSnapshot("Phòng đôi");
        booking.setCheckInDate(LocalDate.of(2027, 6, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 6, 13));
        booking.setTotalAmount(1_500_000L);
        return booking;
    }

    @Test
    void dungMaVaEmail_traVeDuChiTiet() {
        BookingLookupResponse result = service.lookup("ABCD2345", "khach@example.com");

        assertThat(result.bookingCode()).isEqualTo("ABCD2345");
        assertThat(result.status()).isEqualTo("CHO_XAC_NHAN");
        assertThat(result.statusLabel()).isEqualTo("Chờ xác nhận");
        assertThat(result.roomTypeName()).isEqualTo("Phòng đôi");
        assertThat(result.checkInDate()).isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(result.checkOutDate()).isEqualTo(LocalDate.of(2027, 6, 13));
        assertThat(result.nights()).isEqualTo(3);
        assertThat(result.totalAmount()).isEqualTo(1_500_000L);
        assertThat(result.depositAmount()).isZero();
    }

    @Test
    void maChuThuongThuaKhoangTrang_vaEmailKhacHoaThuong_vanTimDuoc() {
        BookingLookupResponse result = service.lookup("  abcd2345 ", " Khach@Example.COM ");

        assertThat(result.bookingCode()).isEqualTo("ABCD2345");
    }

    @ParameterizedTest
    @CsvSource({
            "CHO_XAC_NHAN, Chờ xác nhận",
            "DA_XAC_NHAN, Đã xác nhận",
            "DA_HUY, Đã huỷ",
            "DA_NHAN_PHONG, Đã nhận phòng",
            "DA_TRA_PHONG, Đã trả phòng",
            "DA_HET_HAN, Đã hết hạn giữ chỗ"
    })
    void moiTrangThai_coNhanTiengViet(BookingStatus status, String label) {
        when(bookingRepository.findByBookingCode("ABCD2345")).thenReturn(Optional.of(booking(status)));

        BookingLookupResponse result = service.lookup("ABCD2345", "khach@example.com");

        assertThat(result.status()).isEqualTo(status.name());
        assertThat(result.statusLabel()).isEqualTo(label);
    }

    @Test
    void maKhongTonTai_baoThongBaoChung() {
        when(bookingRepository.findByBookingCode("ZZZZ9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookup("ZZZZ9999", "khach@example.com"))
                .isInstanceOf(BookingLookupNotFoundException.class)
                .hasMessage(BookingLookupNotFoundException.MESSAGE);
    }

    @Test
    void emailKhongKhop_baoCungThongBaoChung() {
        assertThatThrownBy(() -> service.lookup("ABCD2345", "nguoikhac@example.com"))
                .isInstanceOf(BookingLookupNotFoundException.class)
                .hasMessage(BookingLookupNotFoundException.MESSAGE);
    }

    @Test
    void caHaiDeuSai_baoCungThongBaoChung() {
        when(bookingRepository.findByBookingCode("ZZZZ9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookup("ZZZZ9999", "nguoikhac@example.com"))
                .isInstanceOf(BookingLookupNotFoundException.class)
                .hasMessage(BookingLookupNotFoundException.MESSAGE);
    }

    @Test
    void bookingKhongCoEmail_khongTraCuuDuoc() {
        Booking walkIn = booking(BookingStatus.DA_XAC_NHAN);
        walkIn.setGuestEmail(null);
        when(bookingRepository.findByBookingCode("ABCD2345")).thenReturn(Optional.of(walkIn));

        assertThatThrownBy(() -> service.lookup("ABCD2345", ""))
                .isInstanceOf(BookingLookupNotFoundException.class);
    }

    @Test
    void dungSauMotLanSai_vanXemDuoc() {
        assertThatThrownBy(() -> service.lookup("ABCD2345", "sai@example.com"))
                .isInstanceOf(BookingLookupNotFoundException.class);

        assertThat(service.lookup("ABCD2345", "khach@example.com").bookingCode()).isEqualTo("ABCD2345");
    }
}
