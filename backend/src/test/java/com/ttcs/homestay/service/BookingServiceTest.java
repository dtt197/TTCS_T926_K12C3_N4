package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingCreateRequest;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.PriceOverride;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.PriceOverrideRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private OperatingSettingsService operatingSettingsService;

        @Mock
    private PriceOverrideRepository priceOverrideRepository;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        // S2-02 Lát 2: dùng PricingService thật để kiểm tra đúng cách tính giá, chỉ giả lập repository.
        PricingService pricingService =
                new PricingService(roomTypeRepository, priceOverrideRepository, operatingSettingsService);
        bookingService = new BookingService(
                bookingRepository, roomTypeRepository, operatingSettingsService, pricingService);
    }

    @Test
    void bookingDaTaoGiuNguyenSnapshotSauKhiGiaThayDoi_bookingMoiDungGiaMoi() {
        RoomType roomType = new RoomType();
        roomType.setId(4L);
        roomType.setName("Phòng đôi");
        roomType.setStatus(true);
        roomType.setWeekdayPrice(100_000L);
        roomType.setWeekendPrice(200_000L);
        when(roomTypeRepository.findById(4L)).thenReturn(Optional.of(roomType));

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("FRIDAY,SATURDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);

        List<Booking> storedBookings = new ArrayList<>();
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId((long) storedBookings.size() + 1);
            storedBookings.add(booking);
            return booking;
        });

        BookingCreateRequest request = new BookingCreateRequest(
                4L,
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 5));
        BookingResponse originalBooking = bookingService.createBooking(request);

        roomType.setWeekdayPrice(150_000L);
        roomType.setWeekendPrice(300_000L);
        settings.setWeekendDays("SUNDAY");
        BookingResponse newBooking = bookingService.createBooking(request);

        assertThat(originalBooking.weekdayPriceSnapshot()).isEqualTo(100_000L);
        assertThat(originalBooking.weekendPriceSnapshot()).isEqualTo(200_000L);
        assertThat(originalBooking.weekendDaysSnapshot()).isEqualTo("FRIDAY,SATURDAY");
        assertThat(originalBooking.totalAmount()).isEqualTo(500_000L);
        assertThat(storedBookings.get(0).getTotalAmount()).isEqualTo(500_000L);

        assertThat(newBooking.weekdayPriceSnapshot()).isEqualTo(150_000L);
        assertThat(newBooking.weekendPriceSnapshot()).isEqualTo(300_000L);
        assertThat(newBooking.weekendDaysSnapshot()).isEqualTo("SUNDAY");
        assertThat(newBooking.totalAmount()).isEqualTo(600_000L);
        assertThat(storedBookings.get(1).getTotalAmount()).isEqualTo(600_000L);
    }
    
    @Test
    void bookingXuyenSuot3LoaiGia_tongTienDungUuTienGiaDe() {
        RoomType roomType = new RoomType();
        roomType.setId(4L);
        roomType.setName("Phòng đôi");
        roomType.setStatus(true);
        roomType.setWeekdayPrice(100_000L);
        roomType.setWeekendPrice(200_000L);
        when(roomTypeRepository.findById(4L)).thenReturn(Optional.of(roomType));

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("FRIDAY,SATURDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);

        PriceOverride le304 = new PriceOverride();
        le304.setName("Lễ 30/4");
        le304.setStartDate(LocalDate.of(2027, 4, 29));
        le304.setEndDate(LocalDate.of(2027, 5, 1));
        le304.setPricePerNight(900_000L);
        when(priceOverrideRepository.findOverlapping(anyLong(), any(), any())).thenReturn(List.of(le304));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 28/04 thứ Tư 100.000 | 29/04, 30/04 (thứ Sáu), 01/05 (thứ Bảy) giá đè 900.000 | 02/05 Chủ nhật 100.000
        BookingResponse booking = bookingService.createBooking(new BookingCreateRequest(
                4L, LocalDate.of(2027, 4, 28), LocalDate.of(2027, 5, 3)));

        assertThat(booking.totalAmount()).isEqualTo(2_900_000L);
    }
}