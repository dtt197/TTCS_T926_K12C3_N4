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
import com.ttcs.homestay.exception.InvalidPriceQuoteException;
import com.ttcs.homestay.repository.PriceOverrideRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * S2-02 Lát 2: giá đè > giá cuối tuần > giá ngày thường.
 * Phòng đôi: ngày thường 500.000, cuối tuần (đêm thứ Sáu, thứ Bảy) 700.000.
 * Đợt "Lễ 30/4": 29/04/2027 - 01/05/2027, 900.000. 30/04/2027 là thứ Sáu.
 */
@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private PriceOverrideRepository priceOverrideRepository;

    @Mock
    private OperatingSettingsService operatingSettingsService;

    @InjectMocks
    private PricingService pricingService;

    private RoomType phongDoi;
    private OperatingSettings settings;

    @BeforeEach
    void setUp() {
        phongDoi = new RoomType();
        phongDoi.setId(1L);
        phongDoi.setName("Phòng đôi");
        phongDoi.setWeekdayPrice(500_000L);
        phongDoi.setWeekendPrice(700_000L);

        settings = new OperatingSettings();
        settings.setWeekendDays("FRIDAY,SATURDAY");
    }

    private void coSan(PriceOverride... overrides) {
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(phongDoi));
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(priceOverrideRepository.findOverlapping(anyLong(), any(), any())).thenReturn(List.of(overrides));
    }

    private static PriceOverride dot(String name, String start, String end, long price, OffsetDateTime updatedAt) {
        PriceOverride override = new PriceOverride();
        override.setName(name);
        override.setStartDate(LocalDate.parse(start));
        override.setEndDate(LocalDate.parse(end));
        override.setPricePerNight(price);
        override.setUpdatedAt(updatedAt);
        return override;
    }

    private static PriceOverride le304() {
        return dot("Lễ 30/4", "2027-04-29", "2027-05-01", 900_000L, OffsetDateTime.now());
    }

    @Test
    void khoangCoGiaDe_tinhGiaDeChoNhungDemDo() {
        coSan(le304());

        PriceQuoteResponse quote = pricingService.quote(1L, LocalDate.of(2027, 4, 29), LocalDate.of(2027, 5, 2));

        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::priceType)
                .containsOnly(PriceType.OVERRIDE);
        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::label).containsOnly("Lễ 30/4");
        assertThat(quote.totalAmount()).isEqualTo(2_700_000L);
    }

    @Test
    void cuoiTuanKhongCoGiaDe_tinhGiaCuoiTuan() {
        coSan();

        // Đêm 07/05/2027 (thứ Sáu) và 08/05/2027 (thứ Bảy).
        PriceQuoteResponse quote = pricingService.quote(1L, LocalDate.of(2027, 5, 7), LocalDate.of(2027, 5, 9));

        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::priceType)
                .containsExactly(PriceType.WEEKEND, PriceType.WEEKEND);
        assertThat(quote.totalAmount()).isEqualTo(1_400_000L);
    }

    @Test
    void ngayThuongKhongCoGiaDe_tinhGiaNgayThuong() {
        coSan();

        // Đêm thứ Hai 03/05/2027 đến hết đêm thứ Tư 05/05/2027.
        PriceQuoteResponse quote = pricingService.quote(1L, LocalDate.of(2027, 5, 3), LocalDate.of(2027, 5, 6));

        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::priceType)
                .containsOnly(PriceType.WEEKDAY);
        assertThat(quote.totalAmount()).isEqualTo(1_500_000L);
    }

    @Test
    void xuyenSuot3LoaiGia_moiDemDungUuTien() {
        coSan(le304());

        // 28/04 (thứ Tư) .. 02/05 (Chủ nhật), trả phòng 03/05.
        PriceQuoteResponse quote = pricingService.quote(1L, LocalDate.of(2027, 4, 28), LocalDate.of(2027, 5, 3));

        assertThat(quote.nights()).isEqualTo(5);
        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::priceType).containsExactly(
                PriceType.WEEKDAY,   // 28/04 thứ Tư
                PriceType.OVERRIDE,  // 29/04 thứ Năm
                PriceType.OVERRIDE,  // 30/04 thứ Sáu: giá đè thắng giá cuối tuần
                PriceType.OVERRIDE,  // 01/05 thứ Bảy: giá đè thắng giá cuối tuần
                PriceType.WEEKDAY);  // 02/05 Chủ nhật
        assertThat(quote.totalAmount()).isEqualTo(3_700_000L);
    }

    @Test
    void demCuoiCuaDotVanTinhGiaDe_demNgayTraPhongKhongTinh() {
        coSan(le304());

        // Nhận 01/05, trả 02/05: chỉ có đêm 01/05 (đêm cuối của đợt).
        PriceQuoteResponse quote = pricingService.quote(1L, LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 2));

        assertThat(quote.nights()).isEqualTo(1);
        assertThat(quote.nightlyPrices().get(0).priceType()).isEqualTo(PriceType.OVERRIDE);
        assertThat(quote.totalAmount()).isEqualTo(900_000L);
    }

    @Test
    void doiNgayCuoiTuanThanhThuBayChuNhat_demThuSauThanhNgayThuong() {
        settings.setWeekendDays("SATURDAY,SUNDAY");
        coSan();

        PriceQuoteResponse quote = pricingService.quote(1L, LocalDate.of(2027, 5, 7), LocalDate.of(2027, 5, 10));

        assertThat(quote.nightlyPrices()).extracting(NightlyPrice::priceType)
                .containsExactly(PriceType.WEEKDAY, PriceType.WEEKEND, PriceType.WEEKEND);
    }

    @Test
    void haiDotTrungNgay_layDotCapNhatGanNhat() {
        // findOverlapping trả đợt cập nhật gần nhất trước.
        PriceOverride moi = dot("Lễ 30/4 (mới)", "2027-04-29", "2027-05-01", 1_000_000L, OffsetDateTime.now());
        PriceOverride cu = dot("Lễ 30/4 (cũ)", "2027-04-29", "2027-05-01", 800_000L,
                OffsetDateTime.now().minusDays(1));
        coSan(moi, cu);

        PriceQuoteResponse quote = pricingService.quote(1L, LocalDate.of(2027, 4, 30), LocalDate.of(2027, 5, 1));

        assertThat(quote.nightlyPrices().get(0).label()).isEqualTo("Lễ 30/4 (mới)");
        assertThat(quote.totalAmount()).isEqualTo(1_000_000L);
    }

    @Test
    void ngayTraKhongSauNgayNhan_biChan() {
        assertThatThrownBy(() -> pricingService.quote(1L, LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 2)))
                .isInstanceOf(InvalidPriceQuoteException.class)
                .hasMessageContaining("Ngày trả phòng phải sau ngày nhận phòng");
    }

    @Test
    void qua30Dem_biChan() {
        assertThatThrownBy(() -> pricingService.quote(1L, LocalDate.of(2027, 5, 1), LocalDate.of(2027, 6, 1)))
                .isInstanceOf(InvalidPriceQuoteException.class)
                .hasMessageContaining("tối đa 30 đêm");
    }

    @Test
    void loaiPhongChuaCoGiaCuoiTuan_baoLoi() {
        phongDoi.setWeekendPrice(null);
        coSan();

        assertThatThrownBy(() -> pricingService.quote(1L, LocalDate.of(2027, 5, 7), LocalDate.of(2027, 5, 8)))
                .isInstanceOf(InvalidPriceQuoteException.class)
                .hasMessage("Loại phòng chưa có giá cuối tuần");
    }
}
