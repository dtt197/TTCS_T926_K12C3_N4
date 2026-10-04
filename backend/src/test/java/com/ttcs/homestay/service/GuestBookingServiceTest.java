package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.GuestBookingRequest;
import com.ttcs.homestay.dto.booking.GuestBookingResponse;
import com.ttcs.homestay.dto.pricing.NightlyPrice;
import com.ttcs.homestay.dto.pricing.PriceType;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.InvalidGuestBookingException;
import com.ttcs.homestay.exception.RoomUnavailableException;
import com.ttcs.homestay.exception.RoomTypeNotFoundException;
import com.ttcs.homestay.exception.RoomTypeUnavailableException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.Duration;
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
import org.springframework.test.util.ReflectionTestUtils;

/** S2-07 Lát 1: khách gửi yêu cầu đặt phòng. Ngày đặt luôn ở tương lai để test không phụ thuộc hôm nay. */
@ExtendWith(MockitoExtension.class)
class GuestBookingServiceTest {

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

    @BeforeEach
    void setUp() {
        phongDoi = new RoomType();
        phongDoi.setId(1L);
        phongDoi.setName("Phòng đôi");
        phongDoi.setStatus(true);
        phongDoi.setStandardCapacity(2);
        phongDoi.setMaxCapacity(3);
        phongDoi.setWeekdayPrice(500_000L);
        phongDoi.setWeekendPrice(700_000L);
        
        // S2-07 Lát 3: gắn bộ đếm lượt đặt thật (mỗi test một bộ đếm mới, chưa có lượt nào) để không bị null.
        ReflectionTestUtils.setField(guestBookingService, "bookingRateLimiter", new BookingRateLimiter());
    }

    private static GuestBookingRequest request(int guestCount) {
        return new GuestBookingRequest(
        1L,
        CHECK_IN,
        CHECK_OUT,
        "  Nguyễn Văn A  ",
        "0912345678",
        "Khach@Gmail.com",
        guestCount,
        "Đến muộn khoảng 22h",
        true
);
    }

    /** Còn 1 phòng, giá 2 đêm 500.000 + 700.000. */
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
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void guiDuThongTinKhiConPhong_taoBookingMa8KyTuChoXacNhanGiuCho24Gio() {
        conPhong();
        when(bookingCodeGenerator.next()).thenReturn("7KQ2M9XA");

        GuestBookingResponse response = guestBookingService.createGuestBooking(request(2));

        assertThat(response.bookingCode()).hasSize(8).isEqualTo("7KQ2M9XA");
        assertThat(response.status()).isEqualTo("CHO_XAC_NHAN");
        assertThat(response.statusLabel()).isEqualTo("Chờ xác nhận");
        assertThat(response.nights()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualTo(1_200_000L);

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(saved.capture());
        Booking booking = saved.getValue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHO_XAC_NHAN);
        assertThat(Duration.between(booking.getCreatedAt(), booking.getHoldExpiresAt())).isEqualTo(Duration.ofHours(24));
        assertThat(booking.getGuestName()).isEqualTo("Nguyễn Văn A");
        assertThat(booking.getGuestEmail()).isEqualTo("khach@gmail.com");
        assertThat(booking.getGuestCount()).isEqualTo(2);
    }

    @Test
    void maSinhRaBiTrung_tuSinhLaiMaKhac() {
        conPhong();
        when(bookingCodeGenerator.next()).thenReturn("AAAAAAAA", "BBBBBBBB");
        when(bookingRepository.existsByBookingCode("AAAAAAAA")).thenReturn(true);
        when(bookingRepository.existsByBookingCode("BBBBBBBB")).thenReturn(false);

        GuestBookingResponse response = guestBookingService.createGuestBooking(request(2));

        assertThat(response.bookingCode()).isEqualTo("BBBBBBBB");
    }

    @Test
    void hetPhong_biChanKhongTaoBooking() {
        when(roomTypeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(phongDoi));
        when(roomAvailabilityService.availableRooms(phongDoi, CHECK_IN, CHECK_OUT)).thenReturn(0);

        assertThatThrownBy(() -> guestBookingService.createGuestBooking(request(2)))
                .isInstanceOf(RoomUnavailableException.class)
                .hasMessageContaining("đã hết phòng");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void soKhachVuotSucChuaToiDa_biChan() {
        when(roomTypeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(phongDoi));

        assertThatThrownBy(() -> guestBookingService.createGuestBooking(request(4)))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessage("Phòng đôi chỉ nhận tối đa 3 khách, bạn đang chọn 4 khách");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void ngayNhanPhongOQuaKhu_biChan() {
        GuestBookingRequest past = new GuestBookingRequest(
    1L,
    LocalDate.now().minusDays(1),
    LocalDate.now().plusDays(1),
    "Nguyễn Văn A",
    "0912345678",
    "khach@gmail.com",
    2,
    null,
    true
);

        assertThatThrownBy(() -> guestBookingService.createGuestBooking(past))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("quá khứ");
    }

    @Test
    void loaiPhongNgungBan_biChan() {
        phongDoi.setStatus(false);
        when(roomTypeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(phongDoi));

        assertThatThrownBy(() -> guestBookingService.createGuestBooking(request(2)))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("ngừng bán");
    }

    @Test
    void chiTietLoaiPhongDangBan_duocTraVeChoKhach() {
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(phongDoi));

        var details = guestBookingService.getPublicRoomType(1L);

        assertThat(details.id()).isEqualTo(1L);
        assertThat(details.name()).isEqualTo("Phòng đôi");
        assertThat(details.maxCapacity()).isEqualTo(3);
    }

    @Test
    void chiTietLoaiPhongKhongTonTai_baoNotFound() {
        when(roomTypeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guestBookingService.getPublicRoomType(999L))
                .isInstanceOf(RoomTypeNotFoundException.class);
    }

    @Test
    void chiTietLoaiPhongNgungBan_baoUnavailable() {
        phongDoi.setStatus(false);
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(phongDoi));

        assertThatThrownBy(() -> guestBookingService.getPublicRoomType(1L))
                .isInstanceOf(RoomTypeUnavailableException.class)
                .hasMessage("Loại phòng này hiện đã ngừng bán");
    }
    
    @Test
    void khachVuotSucChuaTieuChuan_tongTienBookingGomPhuThu() {
        // S2-06 Lát 2: Phòng đôi chuẩn 2 người, đặt 3 khách, 2 đêm (500.000 + 700.000).
        conPhong();
        when(bookingCodeGenerator.next()).thenReturn("7KQ2M9XA");

        GuestBookingResponse response = guestBookingService.createGuestBooking(request(3));

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(saved.capture());
        Booking booking = saved.getValue();
        assertThat(booking.getExtraGuestCount()).isEqualTo(1);
        assertThat(booking.getExtraPersonFeeSnapshot()).isEqualTo(200_000L);
        assertThat(booking.getSurchargeAmount()).isEqualTo(400_000L); // 1 người × 200.000 × 2 đêm
        assertThat(booking.getTotalAmount()).isEqualTo(1_600_000L);
        assertThat(response.totalAmount()).isEqualTo(1_600_000L);
    }
}