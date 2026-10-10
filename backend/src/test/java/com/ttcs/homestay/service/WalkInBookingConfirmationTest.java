package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.WalkInBookingRequest;
import com.ttcs.homestay.dto.booking.WalkInBookingResponse;
import com.ttcs.homestay.dto.pricing.NightlyPrice;
import com.ttcs.homestay.dto.pricing.PriceType;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingSource;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WalkInBookingConfirmationTest {

    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(10);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

    @Mock private BookingRepository bookingRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RoomAvailabilityService roomAvailabilityService;
    @Mock private PricingService pricingService;
    @Mock private OperatingSettingsService operatingSettingsService;
    @Mock private BookingCodeGenerator bookingCodeGenerator;

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

    private void conPhong201() {
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
        when(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null)).thenReturn(p201);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static WalkInBookingRequest request(String email) {
        return new WalkInBookingRequest(1L, CHECK_IN, CHECK_OUT, "  Nguyễn Văn A  ", "0912345678", email, 2, "Khách quen");
    }

    @Test
    void ketQuaTraDuThongTinDeLeTanDocHoacIn() {
        conPhong201();

        WalkInBookingResponse response = guestBookingService.createWalkInBooking(request(null));

        assertThat(response.guestName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.roomTypeName()).isEqualTo("Phòng đôi");
        assertThat(response.roomNumber()).isEqualTo("201");
        assertThat(response.checkInDate()).isEqualTo(CHECK_IN);
        assertThat(response.checkOutDate()).isEqualTo(CHECK_OUT);
        assertThat(response.nights()).isEqualTo(2);
        assertThat(response.guestCount()).isEqualTo(2);
        assertThat(response.bookingCode()).isEqualTo("7KQ2M9XA");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void trangThaiHienDaXacNhan_khongPhaiChoXacNhan() {
        conPhong201();

        WalkInBookingResponse response = guestBookingService.createWalkInBooking(request(null));

        assertThat(response.status()).isEqualTo(BookingStatus.DA_XAC_NHAN.name());
        assertThat(response.statusLabel()).isEqualTo("Đã xác nhận");
    }

    @Test
    void nguonBookingHienRoLaTaiQuay() {
        conPhong201();

        WalkInBookingResponse response = guestBookingService.createWalkInBooking(request(null));

        assertThat(response.source()).isEqualTo(BookingSource.TAI_QUAY.name());
        assertThat(response.sourceLabel()).isEqualTo("Tại quầy");
    }

    @Test
    void nhapEmail_vanKhongPhatSinhThaoTacGuiEmail() {
        conPhong201();

        WalkInBookingResponse response = guestBookingService.createWalkInBooking(request("khach@gmail.com"));

        assertThat(response.bookingCode()).isEqualTo("7KQ2M9XA");
        // Khi làm S4-04 (email xác nhận), phải loại booking nguồn TAI_QUAY và mở rộng test này.
                assertThat(Arrays.stream(GuestBookingService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .toList())
                .doesNotContain(MailService.class.getName(), JavaMailSender.class.getName());
    }

    @Test
    void toInKhongCoSoDienThoaiEmailHayGhiChu() {
        assertThat(Arrays.stream(WalkInBookingResponse.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("guestPhone", "phone", "guestEmail", "email", "note");
    }

    @Test
    void chuaGanPhong_soPhongDeTrongThayVaLoi() {
        Booking booking = new Booking();
        booking.setBookingCode("ABCD2345");
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setSource(BookingSource.TAI_QUAY);
        booking.setGuestName("Trần Thị B");
        booking.setGuestCount(1);
        booking.setRoomTypeNameSnapshot("Phòng đơn");
        booking.setCheckInDate(CHECK_IN);
        booking.setCheckOutDate(CHECK_IN.plusDays(1));
        booking.setCreatedAt(OffsetDateTime.now());

        WalkInBookingResponse response = WalkInBookingResponse.from(booking);

        assertThat(response.roomNumber()).isNull();
        assertThat(response.nights()).isEqualTo(1);
    }
}