package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.GuestQuoteAlternative;
import com.ttcs.homestay.dto.booking.GuestQuoteResponse;
import com.ttcs.homestay.dto.pricing.NightlyPrice;
import com.ttcs.homestay.dto.pricing.PriceType;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.PriceOverride;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.InvalidGuestBookingException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.PriceOverrideRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * S2-06: khách xem giá tạm tính từng đêm trước khi đặt.
 * 06/05/2030 là thứ Hai; đêm thứ Sáu 10/05 và thứ Bảy 11/05 là cuối tuần.
 * Phòng đôi: chuẩn 2, tối đa 3 khách; ngày thường 500.000, cuối tuần 700.000. Phụ thu thêm người 200.000 / người / đêm.
 */
@ExtendWith(MockitoExtension.class)
class GuestQuoteTest {

    private static final LocalDate MONDAY = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private RoomAvailabilityService roomAvailabilityService;

    @Mock
    private PriceOverrideRepository priceOverrideRepository;

    @Mock
    private OperatingSettingsService operatingSettingsService;

    @Mock
    private BookingCodeGenerator bookingCodeGenerator;

    private GuestBookingService guestBookingService;
    private OperatingSettings settings;

    @BeforeEach
    void setUp() {
        // Dùng PricingService thật để kiểm tra đúng nhãn và giá của S2-02.
        PricingService pricingService =
                new PricingService(roomTypeRepository, priceOverrideRepository, operatingSettingsService);
        guestBookingService = new GuestBookingService(bookingRepository, roomTypeRepository,
                roomAvailabilityService, pricingService, operatingSettingsService, bookingCodeGenerator);

        settings = new OperatingSettings();
        settings.setWeekendDays("FRIDAY,SATURDAY");
        settings.setExtraPersonFee(200_000L);
    }

    private void coSan(RoomType roomType, PriceOverride... overrides) {
        when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(priceOverrideRepository.findOverlapping(anyLong(), any(), any())).thenReturn(List.of(overrides));
    }

    private static RoomType roomType(Long id, String name, long weekdayPrice, long weekendPrice) {
        RoomType roomType = new RoomType();
        roomType.setId(id);
        roomType.setName(name);
        roomType.setStatus(true);
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(3);
        roomType.setWeekdayPrice(weekdayPrice);
        roomType.setWeekendPrice(weekendPrice);
        return roomType;
    }

    // ---- Lát 1: bảng giá từng đêm kèm nhãn ----

    @Test
    void toanNgayThuong_nhanNgayThuongVaTongDung() {
        coSan(roomType(1L, "Phòng đôi", 500_000L, 700_000L));

        GuestQuoteResponse quote = guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(3), 2);

        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::label).containsOnly("Ngày thường");
        assertThat(quote.nights()).isEqualTo(3);
        assertThat(quote.totalAmount()).isEqualTo(1_500_000L);
    }

    @Test
    void coDemCuoiTuan_nhanCuoiTuanVaGiaCuoiTuan() {
        coSan(roomType(1L, "Phòng đôi", 500_000L, 700_000L));

        // Thứ Năm 09/05 đến hết đêm Chủ nhật 12/05.
        GuestQuoteResponse quote = guestBookingService.quote(1L, MONDAY.plusDays(3), MONDAY.plusDays(7), 2);

        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::label)
                .containsExactly("Ngày thường", "Cuối tuần", "Cuối tuần", "Ngày thường");
        assertThat(quote.totalAmount()).isEqualTo(2_400_000L);
    }

    @Test
    void trungDotGiaDe_hienTenDotKeCaDemCuoiTuan() {
        PriceOverride le = new PriceOverride();
        le.setName("Lễ 10/5");
        le.setStartDate(MONDAY.plusDays(4));
        le.setEndDate(MONDAY.plusDays(5));
        le.setPricePerNight(900_000L);
        le.setUpdatedAt(OffsetDateTime.now());
        coSan(roomType(1L, "Phòng đôi", 500_000L, 700_000L), le);

        GuestQuoteResponse quote = guestBookingService.quote(1L, MONDAY.plusDays(3), MONDAY.plusDays(7), 2);

        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::priceType).containsExactly(
                PriceType.WEEKDAY, PriceType.OVERRIDE, PriceType.OVERRIDE, PriceType.WEEKDAY);
        assertThat(quote.nightlyPrices().get(1).label()).isEqualTo("Lễ 10/5");
        assertThat(quote.totalAmount()).isEqualTo(2_800_000L);
    }

    @Test
    void doiLoaiPhong_giaTheoLoaiPhongMoi() {
        coSan(roomType(2L, "Phòng gia đình", 800_000L, 1_000_000L));

        GuestQuoteResponse quote = guestBookingService.quote(2L, MONDAY.plusDays(3), MONDAY.plusDays(5), 2);

        assertThat(quote.roomTypeName()).isEqualTo("Phòng gia đình");
        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::price).containsExactly(800_000L, 1_000_000L);
        assertThat(quote.totalAmount()).isEqualTo(1_800_000L);
    }

    @Test
    void loaiPhongNgungBan_biTuChoi() {
        RoomType ngungBan = roomType(3L, "Phòng cũ", 500_000L, 700_000L);
        ngungBan.setStatus(false);
        when(roomTypeRepository.findById(3L)).thenReturn(Optional.of(ngungBan));

        assertThatThrownBy(() -> guestBookingService.quote(3L, MONDAY, MONDAY.plusDays(1), 2))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("ngừng bán");
    }

    @Test
    void khoangNgaySai_biTuChoi() {
        assertThatThrownBy(() -> guestBookingService.quote(1L, MONDAY, MONDAY, 2))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("ít nhất một đêm");
        assertThatThrownBy(() -> guestBookingService.quote(1L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 2))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("quá khứ");
        assertThatThrownBy(() -> guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(31), 2))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("tối đa 30 đêm");
    }

    @Test
    void checkInToiDa12Thang_quoteHopLe() {
        LocalDate checkIn = PublicBookingDatePolicy.maximumCheckInDate(PublicBookingDatePolicy.today());
        coSan(roomType(1L, "Phòng đôi", 500_000L, 700_000L));

        GuestQuoteResponse quote = guestBookingService.quote(1L, checkIn, checkIn.plusDays(1), 2);

        assertThat(quote.checkIn()).isEqualTo(checkIn);
    }

    @Test
    void checkInQua12ThangVaNam9999_quoteBiTuChoi() {
        LocalDate maximumCheckIn = PublicBookingDatePolicy.maximumCheckInDate(PublicBookingDatePolicy.today());

        assertThatThrownBy(() -> guestBookingService.quote(
                        1L, maximumCheckIn.plusDays(1), maximumCheckIn.plusDays(2), 2))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("không được quá 12 tháng");
        assertThatThrownBy(() -> guestBookingService.quote(
                        1L, LocalDate.of(9999, 1, 1), LocalDate.of(9999, 1, 2), 2))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("không được quá 12 tháng");
    }

    @Test
    void kyNghiDung30Dem_quoteVanHopLe() {
        coSan(roomType(1L, "Phòng đôi", 500_000L, 700_000L));

        GuestQuoteResponse quote = guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(30), 2);

        assertThat(quote.nights()).isEqualTo(30);
    }

    // ---- Lát 2: phụ thu thêm người (mỗi người vượt sức chứa tiêu chuẩn, mỗi đêm) ----

    @Test
    void soKhachBangSucChuaTieuChuan_khongPhuThu() {
        coSan(roomType(1L, "Phòng đôi", 500_000L, 700_000L));

        GuestQuoteResponse quote = guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(2), 2);

        assertThat(quote.extraGuests()).isZero();
        assertThat(quote.surchargeAmount()).isZero();
        assertThat(quote.totalAmount()).isEqualTo(quote.nightsTotal()).isEqualTo(1_000_000L);
    }

    @Test
    void vuot1Nguoi_phuThuMoiNguoiMoiDem() {
        coSan(roomType(1L, "Phòng đôi", 500_000L, 700_000L));

        GuestQuoteResponse quote = guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(2), 3);

        assertThat(quote.overCapacity()).isFalse();
        assertThat(quote.maxCapacity()).isEqualTo(3);
        assertThat(quote.extraGuests()).isEqualTo(1);
        assertThat(quote.extraPersonFee()).isEqualTo(200_000L);
        assertThat(quote.surchargeAmount()).isEqualTo(400_000L); // 1 người × 200.000 × 2 đêm
        assertThat(quote.totalAmount()).isEqualTo(1_400_000L);
    }

    @Test
    void vuot1Nguoi_loaiPhongCoPhuThuRieng_quoteDungMucRieng() {
        RoomType phongDoi = roomType(1L, "Phòng đôi", 500_000L, 700_000L);
        phongDoi.setExtraPersonFee(250_000L);
        coSan(phongDoi);

        GuestQuoteResponse quote = guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(2), 3);

        assertThat(quote.extraPersonFee()).isEqualTo(250_000L);
        assertThat(quote.surchargeAmount()).isEqualTo(500_000L);
    }

    @Test
    void vuot1Nguoi_loaiPhongKhongCoPhuThuRieng_quoteFallbackVeOperatingSettings() {
        settings.setExtraPersonFee(300_000L);
        coSan(roomType(1L, "Phòng đôi", 500_000L, 700_000L));

        GuestQuoteResponse quote = guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(2), 3);

        assertThat(quote.extraPersonFee()).isEqualTo(300_000L);
        assertThat(quote.surchargeAmount()).isEqualTo(600_000L);
    }

    @Test
    void soKhachVuotSucChuaToiDa_quoteTraVeDuThongTinVuotSucChua() {
        RoomType phongDoi = roomType(1L, "Phòng đôi", 500_000L, 700_000L);
        coSan(phongDoi);
        when(roomTypeRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(phongDoi));

        GuestQuoteResponse quote = guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(2), 6);

        assertThat(quote.overCapacity()).isTrue();
        assertThat(quote.guestCount()).isEqualTo(6);
        assertThat(quote.maxCapacity()).isEqualTo(3);
        assertThat(quote.roomTypeName()).isEqualTo("Phòng đôi");
        assertThat(quote.minimumRooms()).isEqualTo(2);
        assertThat(quote.alternatives()).isEmpty();
    }

    @Test
    void alternativesChiGomLoaiPhongDuSucChuaDangBanConPhongVaGiaDung() {
        RoomType phongDoi = roomType(1L, "Phòng đôi", 500_000L, 700_000L);
        RoomType phongNho = roomType(2L, "Phòng ba", 600_000L, 800_000L);
        phongNho.setMaxCapacity(5);
        RoomType phongGiaDinh = roomType(3L, "Phòng gia đình", 800_000L, 1_000_000L);
        phongGiaDinh.setCode("FAMILY");
        phongGiaDinh.setStandardCapacity(6);
        phongGiaDinh.setMaxCapacity(6);
        RoomType phongSuite = roomType(6L, "Phòng suite", 900_000L, 1_100_000L);
        phongSuite.setCode("SUITE");
        phongSuite.setStandardCapacity(6);
        phongSuite.setMaxCapacity(7);
        RoomType phongNgungBan = roomType(4L, "Phòng ngừng bán", 900_000L, 1_100_000L);
        phongNgungBan.setMaxCapacity(8);
        phongNgungBan.setStatus(false);
        RoomType phongHet = roomType(5L, "Phòng hết", 1_000_000L, 1_200_000L);
        phongHet.setMaxCapacity(8);

        coSan(phongDoi);
        when(roomTypeRepository.findAllByOrderByCodeAsc())
                .thenReturn(List.of(phongDoi, phongNho, phongGiaDinh, phongNgungBan, phongHet, phongSuite));
        when(roomAvailabilityService.availableRooms(phongGiaDinh, MONDAY, MONDAY.plusDays(2))).thenReturn(1);
        when(roomAvailabilityService.availableRooms(phongSuite, MONDAY, MONDAY.plusDays(2))).thenReturn(1);
        when(roomAvailabilityService.availableRooms(phongHet, MONDAY, MONDAY.plusDays(2))).thenReturn(0);
        when(priceOverrideRepository.findOverlapping(3L, MONDAY, MONDAY.plusDays(1))).thenReturn(List.of());
        when(priceOverrideRepository.findOverlapping(6L, MONDAY, MONDAY.plusDays(1))).thenReturn(List.of());

        GuestQuoteResponse quote = guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(2), 6);

        assertThat(quote.minimumRooms()).isEqualTo(2);
        assertThat(quote.alternatives()).containsExactly(
                new GuestQuoteAlternative(3L, "Phòng gia đình", 6, 1_600_000L),
                new GuestQuoteAlternative(6L, "Phòng suite", 7, 1_800_000L));
    }

    @Test
    void vuot2Nguoi_phuThuGapDoi() {
        RoomType giaDinh = roomType(2L, "Phòng gia đình", 800_000L, 1_000_000L);
        giaDinh.setMaxCapacity(5);
        coSan(giaDinh);

        GuestQuoteResponse quote = guestBookingService.quote(2L, MONDAY, MONDAY.plusDays(2), 4);

        assertThat(quote.extraGuests()).isEqualTo(2);
        assertThat(quote.surchargeAmount()).isEqualTo(800_000L); // 2 người × 200.000 × 2 đêm
        assertThat(quote.totalAmount()).isEqualTo(2_400_000L);
    }

    @Test
    void doiMucPhuThuTrongThamSo_tamTinhDungMucMoi() {
        settings.setExtraPersonFee(300_000L);
        coSan(roomType(1L, "Phòng đôi", 500_000L, 700_000L));

        GuestQuoteResponse quote = guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(2), 3);

        assertThat(quote.surchargeAmount()).isEqualTo(600_000L);
    }

    @Test
    void soKhachNhoHon1_biTuChoi() {
        assertThatThrownBy(() -> guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(1), 0))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("ít nhất là 1");
    }
}