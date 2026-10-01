package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingCreateRequest;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private BookingService bookingService;

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
}