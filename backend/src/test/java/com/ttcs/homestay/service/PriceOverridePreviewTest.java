package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.pricing.PreviewNight;
import com.ttcs.homestay.dto.pricing.PriceOverridePreviewRequest;
import com.ttcs.homestay.dto.pricing.PriceOverridePreviewResponse;
import com.ttcs.homestay.dto.pricing.PriceType;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.PriceOverride;
import com.ttcs.homestay.entity.RoomType;
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
 * S2-02 Lát 4: xem trước giá từng đêm trước khi lưu.
 * Khoảng 30/04/2027 (thứ Sáu) – 05/05/2027 (thứ Tư) = 6 đêm, cuối tuần là đêm thứ Sáu và thứ Bảy.
 */
@ExtendWith(MockitoExtension.class)
class PriceOverridePreviewTest {

    private static final LocalDate START = LocalDate.of(2027, 4, 30);
    private static final LocalDate END = LocalDate.of(2027, 5, 5);

    @Mock
    private PriceOverrideRepository priceOverrideRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private OperatingSettingsService operatingSettingsService;

    private PriceOverrideService priceOverrideService;

    @BeforeEach
    void setUp() {
        // Dùng PricingService thật để kiểm tra đúng hàm ưu tiên giá của Lát 2.
        PricingService pricingService =
                new PricingService(roomTypeRepository, priceOverrideRepository, operatingSettingsService);
        priceOverrideService = new PriceOverrideService(priceOverrideRepository, roomTypeRepository, pricingService);

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("FRIDAY,SATURDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
    }

    private void coSan(RoomType roomType, PriceOverride... overrides) {
        when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
        when(priceOverrideRepository.findOverlapping(anyLong(), any(), any())).thenReturn(List.of(overrides));
    }

    private static RoomType roomType(Long id, Long weekdayPrice, Long weekendPrice) {
        RoomType roomType = new RoomType();
        roomType.setId(id);
        roomType.setName("Phòng " + id);
        roomType.setWeekdayPrice(weekdayPrice);
        roomType.setWeekendPrice(weekendPrice);
        return roomType;
    }

    private static PriceOverride dot(Long id, String name, LocalDate start, LocalDate end, long price) {
        PriceOverride override = new PriceOverride();
        override.setId(id);
        override.setName(name);
        override.setStartDate(start);
        override.setEndDate(end);
        override.setPricePerNight(price);
        override.setUpdatedAt(OffsetDateTime.now());
        return override;
    }

    private PriceOverridePreviewResponse xemTruoc(Long roomTypeId, Long price, Long excludeId) {
        return priceOverrideService.preview(new PriceOverridePreviewRequest(roomTypeId, START, END, price, excludeId));
    }

    @Test
    void khoang30_4Den5_5_hien6DongVoiGiaHienTai() {
        coSan(roomType(1L, 500_000L, 700_000L));

        PriceOverridePreviewResponse preview = xemTruoc(1L, null, null);

        assertThat(preview.nights()).isEqualTo(6);
        assertThat(preview.nightlyPrices()).extracting(PreviewNight::date)
                .startsWith(START).endsWith(END);
        assertThat(preview.nightlyPrices()).extracting(PreviewNight::currentType).containsExactly(
                PriceType.WEEKEND, PriceType.WEEKEND, // 30/04 thứ Sáu, 01/05 thứ Bảy
                PriceType.WEEKDAY, PriceType.WEEKDAY, PriceType.WEEKDAY, PriceType.WEEKDAY);
        assertThat(preview.currentTotal()).isEqualTo(3_400_000L);
        assertThat(preview.newTotal()).isNull();
        assertThat(preview.conflict()).isNull();
    }

    @Test
    void doiLoaiPhong_giaHienTaiTheoLoaiPhongMoi() {
        coSan(roomType(2L, 300_000L, 400_000L));

        PriceOverridePreviewResponse preview = xemTruoc(2L, null, null);

        assertThat(preview.nightlyPrices().get(0).currentPrice()).isEqualTo(400_000L);
        assertThat(preview.currentTotal()).isEqualTo(2_000_000L);
    }

    @Test
    void nhapGia100k_giaSauKhiLuu100kMoiDem() {
        coSan(roomType(1L, 500_000L, 700_000L));

        PriceOverridePreviewResponse preview = xemTruoc(1L, 100_000L, null);

        assertThat(preview.nightlyPrices()).extracting(PreviewNight::newPrice).containsOnly(100_000L);
        assertThat(preview.newTotal()).isEqualTo(600_000L);
    }

    @Test
    void suaDotCu_giaHienTaiKhongTinhChinhNo() {
        PriceOverride dangSua = dot(10L, "Lễ 30/4", START, END, 900_000L);
        coSan(roomType(1L, 500_000L, 700_000L), dangSua);

        PriceOverridePreviewResponse preview = xemTruoc(1L, 900_000L, 10L);

        assertThat(preview.nightlyPrices()).extracting(PreviewNight::currentType)
                .doesNotContain(PriceType.OVERRIDE);
        assertThat(preview.currentTotal()).isEqualTo(3_400_000L);
        assertThat(preview.conflict()).isNull();
    }

    @Test
    void trungDotKhac_coThongBaoXungDot() {
        PriceOverride dotKhac = dot(11L, "Lễ 30/4", LocalDate.of(2027, 4, 29), LocalDate.of(2027, 5, 1), 900_000L);
        coSan(roomType(1L, 500_000L, 700_000L), dotKhac);

        PriceOverridePreviewResponse preview = xemTruoc(1L, 1_000_000L, null);

        assertThat(preview.conflict()).contains("\"Lễ 30/4\"").contains("29/04/2027 – 01/05/2027");
        assertThat(preview.nightlyPrices().get(0).currentType()).isEqualTo(PriceType.OVERRIDE);
        assertThat(preview.nightlyPrices().get(0).currentLabel()).isEqualTo("Lễ 30/4");
    }

    @Test
    void loaiPhongChuaCoGiaCuoiTuan_giaHienTaiDeTrongKhongBaoLoi() {
        coSan(roomType(3L, 500_000L, null));

        PriceOverridePreviewResponse preview = xemTruoc(3L, 800_000L, null);

        assertThat(preview.nightlyPrices().get(0).currentPrice()).isNull();
        assertThat(preview.nightlyPrices().get(2).currentPrice()).isEqualTo(500_000L);
        assertThat(preview.currentTotal()).isNull();
        assertThat(preview.newTotal()).isEqualTo(4_800_000L);
    }
}