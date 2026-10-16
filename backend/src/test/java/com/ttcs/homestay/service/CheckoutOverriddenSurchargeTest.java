package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.CheckoutPreviewResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.BookingSurcharge;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.SurchargeType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.BookingSettlementRepository;
import com.ttcs.homestay.repository.BookingSurchargeRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** S4-02 Lát 4: bảng tính tiền trả phòng dùng đúng giá đã sửa và hiện lý do. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutOverriddenSurchargeTest {
    @Mock RoomRepository roomRepository;
    @Mock BookingRepository bookingRepository;
    @Mock BookingSettlementRepository settlementRepository;
    @Mock BookingSurchargeRepository surchargeRepository;
    @Mock RoomStatusHistoryRepository historyRepository;
    @Mock BookingDepositService depositService;
    @Mock OperatingSettingsRepository settingsRepository;

    @Test
    void bangTinhTienDungGiaDaSua() {
        CheckoutService service = new CheckoutService(roomRepository, bookingRepository, settlementRepository,
                surchargeRepository, historyRepository, depositService, settingsRepository,
                Clock.fixed(OffsetDateTime.parse("2026-10-12T10:00:00+07:00").toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")));
        OperatingSettings settings = new OperatingSettings();
        settings.setId(7L);
        settings.setCheckOutTime(LocalTime.NOON);
        settings.setLateCheckoutFeePerHour(100_000L);
        settings.setCreatedAt(OffsetDateTime.parse("2026-10-01T00:00:00+07:00"));
        when(settingsRepository.findFirstByCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(any()))
                .thenReturn(Optional.of(settings));
        Room room = new Room();
        room.setId(12L);
        room.setRoomNumber("201");
        room.setStatus(RoomStatus.DANG_O);
        Booking booking = new Booking();
        booking.setId(44L);
        booking.setBookingCode("BK-44");
        booking.setGuestName("Khách test");
        booking.setRoom(room);
        booking.setStatus(BookingStatus.DA_NHAN_PHONG);
        booking.setCheckInDate(LocalDate.of(2026, 10, 10));
        booking.setCheckOutDate(LocalDate.of(2026, 10, 12));
        booking.setTotalAmount(1_000_000);
        BookingSurcharge line = new BookingSurcharge();
        line.setId(1L);
        line.setBooking(booking);
        line.setSurchargeType(SurchargeType.THEM_NGUOI);
        line.setQuantity(1);
        line.setUnitPrice(100_000L);
        line.setDefaultUnitPrice(150_000L);
        line.setPriceOverrideReason("Khách quen, chủ đồng ý giảm");
        line.setNights(2);
        line.setAmount(200_000L);
        when(roomRepository.findById(12L)).thenReturn(Optional.of(room));
        when(bookingRepository.findByRoomAndStatus(12L, BookingStatus.DA_NHAN_PHONG)).thenReturn(List.of(booking));
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.ZERO);
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of(line));

        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.bookingSurcharges().get(0).priceOverridden()).isTrue();
        assertThat(preview.bookingSurcharges().get(0).priceOverrideReason()).isEqualTo("Khách quen, chủ đồng ý giảm");
        assertThat(preview.bookingSurchargeTotal()).isEqualByComparingTo("200000");
        assertThat(preview.settlementTotalBeforeDeposit()).isEqualByComparingTo("1200000");
    }
}