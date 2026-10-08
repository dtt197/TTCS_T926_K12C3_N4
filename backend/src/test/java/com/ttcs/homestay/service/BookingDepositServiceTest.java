package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingConfirmRequest;
import com.ttcs.homestay.dto.booking.BookingConfirmResponse;
import com.ttcs.homestay.entity.BookingDeposit;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.exception.DuplicatePaymentException;
import com.ttcs.homestay.exception.InvalidDepositException;
import com.ttcs.homestay.repository.BookingDepositRepository;
import com.ttcs.homestay.repository.BookingRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BookingDepositServiceTest {

    @Mock
    private BookingDepositRepository bookingDepositRepository;

    @Mock
    private BookingRepository bookingRepository;

    private BookingDepositService service;

    @BeforeEach
    void setUp() {
        service = new BookingDepositService(bookingDepositRepository, bookingRepository);
    }

    @Test
    void xacNhanBooking_khongTimThay_throwsNotFound() {
        when(bookingRepository.findById(1L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.confirmBooking(1L, new BookingConfirmRequest(
                BigDecimal.valueOf(500_000), "CASH", LocalDate.now(), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Không tìm thấy booking");
        verify(bookingDepositRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void xacNhanBooking_khongSapXacNhan_throwsConflict() {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setStatus(BookingStatus.DA_XAC_NHAN);

        when(bookingRepository.findById(1L)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> service.confirmBooking(1L, new BookingConfirmRequest(
                BigDecimal.valueOf(500_000), "CASH", LocalDate.now(), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("không ở trạng thái chờ xác nhận");
        verify(bookingDepositRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void xacNhanBooking_coTheCọc_throwsDuplicate() {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);

        when(bookingRepository.findById(1L)).thenReturn(java.util.Optional.of(booking));
        when(bookingDepositRepository.existsByBooking(booking)).thenReturn(true);

        assertThatThrownBy(() -> service.confirmBooking(1L, new BookingConfirmRequest(
                BigDecimal.valueOf(500_000), "CASH", LocalDate.now(), null, null)))
                .isInstanceOf(DuplicatePaymentException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void xacNhanBooking_soTienKhongDuong_throws() {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);

        when(bookingRepository.findById(1L)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> service.confirmBooking(1L, new BookingConfirmRequest(
                null, "CASH", null, null, null)))
                .isInstanceOf(InvalidDepositException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void xacNhanBooking_chienDichVuoiKhongAm_throws() {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);

        when(bookingRepository.findById(1L)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> service.confirmBooking(1L, new BookingConfirmRequest(
                BigDecimal.valueOf(-1000), "CASH", null, null, null)))
                .isInstanceOf(InvalidDepositException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void xacNhanBooking_chienDichVuoiRong_throws() {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);

        when(bookingRepository.findById(1L)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> service.confirmBooking(1L, new BookingConfirmRequest(
                BigDecimal.valueOf(500_000), null, null, null, null)))
                .isInstanceOf(InvalidDepositException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void xacNhanBooking_chienDichVuoiChuyenKhoan_thiLoiMaThamChieu_nhanDuocException() {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);

        when(bookingRepository.findById(1L)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> service.confirmBooking(1L, new BookingConfirmRequest(
                BigDecimal.valueOf(500_000), "BANK_TRANSFER", LocalDate.now(), null, null)))
                .isInstanceOf(InvalidDepositException.class)
                .hasMessageContaining("Mã tham chiếu");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void xacNhanBooking_chienDichVuoiChuyenKhoan_coMaThamChieu_thanhCong() {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);

        BookingConfirmRequest request = new BookingConfirmRequest(
                BigDecimal.valueOf(500_000),
                "BANK_TRANSFER",
                LocalDate.now(),
                "123456789",
                "admin"
        );

        when(bookingRepository.findById(1L)).thenReturn(java.util.Optional.of(booking));
        when(bookingDepositRepository.save(any(BookingDeposit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingConfirmResponse result = service.confirmBooking(1L, request);

        assertThat(result.deposit().paymentReference()).isEqualTo("123456789");
        assertThat(result.status()).isEqualTo("DA_XAC_NHAN");
    }
}
