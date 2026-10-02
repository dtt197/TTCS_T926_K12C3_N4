package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.pricing.NightlyPrice;
import com.ttcs.homestay.dto.pricing.PriceQuoteResponse;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * S2-06 Lát 1: khách xem giá tạm tính từng đêm trước khi đặt.
 * 06/05/2030 là thứ Hai; đêm thứ Sáu 10/05 và thứ Bảy 11/05 là cuối tuần.
 * Phòng đôi: ngày thường 500.000, cuối tuần 700.000. Phòng gia đình: 800.000 / 1.000.000.
 */
@ExtendWith(MockitoExtension.class)
class GuestQuoteTest {

    private static final LocalDate MONDAY = LocalDate.of(2030, 5, 6);

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

    @BeforeEach
    void setUp() {
        // Dùng PricingService thật để kiểm tra đúng nhãn và giá của S2-02.
        PricingService pricingService =
                new PricingService(roomTypeRepository, priceOverrideRepository, operatingSettingsService);
        guestBookingService = new GuestBookingService(bookingRepository, roomTypeRepository,
                roomAvailabilityService, pricingService, operatingSettingsService, bookingCodeGenerator);
    }

    private void coSan(RoomType roomType, PriceOverride... overrides) {
        when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("FRIDAY,SATURDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(priceOverrideRepository.findOverlapping(anyLong(), any(), any())).thenReturn(List.of(overrides));
    }

    private static RoomType roomType(Long id, String name, long weekdayPrice, long weekendPrice) {
        RoomType roomType = new RoomType();
        roomType.setId(id);
        roomType.setName(name);
        roomType.setStatus(true);
        roomType.setMaxCapacity(3);
        roomType.setWeekdayPrice(weekdayPrice);
        roomType.setWeekendPrice(weekendPrice);
        return roomType;
    }

    @Test
    void toanNgayThuong_nhanNgayThuongVaTongDung() {
        coSan(roomType(1L, "Phòng đôi", 500_000L, 700_000L));

        PriceQuoteResponse quote = guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(3));

        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::label).containsOnly("Ngày thường");
        assertThat(quote.nights()).isEqualTo(3);
        assertThat(quote.totalAmount()).isEqualTo(1_500_000L);
    }

    @Test
    void coDemCuoiTuan_nhanCuoiTuanVaGiaCuoiTuan() {
        coSan(roomType(1L, "Phòng đôi", 500_000L, 700_000L));

        // Thứ Năm 09/05 đến hết đêm Chủ nhật 12/05.
        PriceQuoteResponse quote = guestBookingService.quote(1L, MONDAY.plusDays(3), MONDAY.plusDays(7));

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

        PriceQuoteResponse quote = guestBookingService.quote(1L, MONDAY.plusDays(3), MONDAY.plusDays(7));

        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::priceType).containsExactly(
                PriceType.WEEKDAY, PriceType.OVERRIDE, PriceType.OVERRIDE, PriceType.WEEKDAY);
        assertThat(quote.nightlyPrices().get(1).label()).isEqualTo("Lễ 10/5");
        assertThat(quote.totalAmount()).isEqualTo(2_800_000L);
    }

    @Test
    void doiLoaiPhong_giaTheoLoaiPhongMoi() {
        coSan(roomType(2L, "Phòng gia đình", 800_000L, 1_000_000L));

        PriceQuoteResponse quote = guestBookingService.quote(2L, MONDAY.plusDays(3), MONDAY.plusDays(5));

        assertThat(quote.roomTypeName()).isEqualTo("Phòng gia đình");
        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::price).containsExactly(800_000L, 1_000_000L);
        assertThat(quote.totalAmount()).isEqualTo(1_800_000L);
    }

    @Test
    void loaiPhongNgungBan_biTuChoi() {
        RoomType ngungBan = roomType(3L, "Phòng cũ", 500_000L, 700_000L);
        ngungBan.setStatus(false);
        when(roomTypeRepository.findById(3L)).thenReturn(Optional.of(ngungBan));

        assertThatThrownBy(() -> guestBookingService.quote(3L, MONDAY, MONDAY.plusDays(1)))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("ngừng bán");
    }

    @Test
    void khoangNgaySai_biTuChoi() {
        assertThatThrownBy(() -> guestBookingService.quote(1L, MONDAY, MONDAY))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("ít nhất một đêm");
        assertThatThrownBy(() -> guestBookingService.quote(1L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("quá khứ");
        assertThatThrownBy(() -> guestBookingService.quote(1L, MONDAY, MONDAY.plusDays(31)))
                .isInstanceOf(InvalidGuestBookingException.class)
                .hasMessageContaining("tối đa 30 đêm");
    }
}