package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.GuestBookingRequest;
import com.ttcs.homestay.dto.booking.WalkInBookingRequest;
import com.ttcs.homestay.dto.pricing.NightlyPrice;
import com.ttcs.homestay.dto.pricing.PriceType;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingSource;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.RoomUnavailableException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** S3-02 Lát 1: booking trực tuyến và booking tại quầy (S3-06) đều giữ một phòng cụ thể khi được tạo. */
@ExtendWith(MockitoExtension.class)
class BookingRoomHoldingTest {

    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(10);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private RoomAvailabilityService roomAvailabilityService;

    @Mock
    private PricingService pricingService;

    @Mock
    private OperatingSettingsService operatingSettingsService;

    @Mock
    private BookingCodeGenerator bookingCodeGenerator;

    @InjectMocks
    private GuestBookingService guestBookingService;

    private RoomType phongDoi;
    private Room p201;

    @BeforeEach
    void setUp() {
        phongDoi = new RoomType();
        phongDoi.setId(1L);
        phongDoi.setName("Phòng đôi");
        phongDoi.setStatus(true);
        phongDoi.setStandardCapacity(2);
        phongDoi.setMaxCapacity(3);
        phongDoi.setNumberOfBeds(1);
        phongDoi.setWeekdayPrice(500_000L);
        phongDoi.setWeekendPrice(700_000L);
        phongDoi.setExtraGuestFee(200_000L);
        p201 = new Room();
        p201.setId(201L);
        p201.setRoomNumber("201");
        ReflectionTestUtils.setField(guestBookingService, "bookingRateLimiter", new BookingRateLimiter());
    }

    /** Còn phòng, giá 2 đêm 500.000 + 700.000. */
    private void conPhong() {
        when(roomTypeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(phongDoi));
        when(roomAvailabilityService.availableRooms(phongDoi, CHECK_IN, CHECK_OUT)).thenReturn(1);
        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("FRIDAY,SATURDAY");
        settings.setExtraPersonFee(200_000L);
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(pricingService.priceNights(any(), any(), any(), any())).thenReturn(List.of(
                new NightlyPrice(CHECK_IN, PriceType.WEEKDAY, "Ngày thường", 500_000L),
                new NightlyPrice(CHECK_IN.plusDays(1), PriceType.WEEKEND, "Cuối tuần", 700_000L)));
        when(bookingCodeGenerator.next()).thenReturn("7KQ2M9XA");
    }

    private static GuestBookingRequest guestRequest() {
        return new GuestBookingRequest(1L, CHECK_IN, CHECK_OUT, "Nguyễn Văn A", "0912345678",
                "khach@gmail.com", 2, null, true);
    }

    private static WalkInBookingRequest walkInRequest() {
        return new WalkInBookingRequest(1L, CHECK_IN, CHECK_OUT, "Khách tại quầy", "0912345678", null, 2, null);
    }

    private Booking savedBooking() {
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void khachDatTrucTuyen_bookingGiuPhongDuocGan() {
        conPhong();
        when(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null)).thenReturn(p201);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        guestBookingService.createGuestBooking(guestRequest());

        assertThat(savedBooking().getRoom()).isSameAs(p201);
    }

    @Test
    void bookingTaiQuay_xacNhanNgayVaGiuPhongDuocGan() {
        conPhong();
        when(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null)).thenReturn(p201);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        guestBookingService.createWalkInBooking(walkInRequest());

        Booking booking = savedBooking();
        assertThat(booking.getRoom()).isSameAs(p201);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        assertThat(booking.getSource()).isEqualTo(BookingSource.TAI_QUAY);
    }

    @Test
    void bookingTaiQuay_khongConPhongDeGan_baoHetPhongVaKhongLuu() {
        conPhong();
        when(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null))
                .thenThrow(RoomAvailabilityService.unavailable(phongDoi));

        assertThatThrownBy(() -> guestBookingService.createWalkInBooking(walkInRequest()))
                .isInstanceOf(RoomUnavailableException.class)
                .hasMessageContaining("Phòng đôi đã hết phòng");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void coSoDuLieuTuChoiViTrungPhong_baoHetPhongThayViLoi500() {
        conPhong();
        when(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null)).thenReturn(p201);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataIntegrityViolationException("could not execute statement", new SQLException(
                "conflicting key value violates exclusion constraint \"bookings_room_no_overlap\"")))
                .when(bookingRepository).flush();

        assertThatThrownBy(() -> guestBookingService.createGuestBooking(guestRequest()))
                .isInstanceOf(RoomUnavailableException.class)
                .hasMessageContaining("Phòng đôi đã hết phòng");
    }

    @Test
    void loiCoSoDuLieuKhac_khongBiDoiThanhHetPhong() {
        conPhong();
        when(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null)).thenReturn(p201);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataIntegrityViolationException("could not execute statement", new SQLException(
                "duplicate key value violates unique constraint \"bookings_booking_code_unique\"")))
                .when(bookingRepository).flush();

        assertThatThrownBy(() -> guestBookingService.createWalkInBooking(walkInRequest()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
