package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.settings.CancellationTierRequest;
import com.ttcs.homestay.dto.settings.OperatingSettingsRequest;
import com.ttcs.homestay.dto.settings.OperatingSettingsResponse;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.exception.InvalidSettingsException;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperatingSettingsServiceTest {

    @Mock
    private OperatingSettingsRepository settingsRepository;

    @InjectMocks
    private OperatingSettingsService settingsService;

    @Test
    void luuThamSo_taoPhienBanMoi_ghiNguoiSuaVaThoiDiem() {
        // AC4: không sửa đè, tạo phiên bản mới kèm người sửa và thời điểm sửa
        when(settingsRepository.save(any(OperatingSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OperatingSettingsResponse response = settingsService.update(
                request(LocalTime.of(14, 0), LocalTime.of(12, 0), List.of(tier(24, 50), tier(72, 100), tier(0, 0))),
                5L, "Chủ Nhà");

        ArgumentCaptor<OperatingSettings> saved = ArgumentCaptor.forClass(OperatingSettings.class);
        verify(settingsRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getCreatedByUserId()).isEqualTo(5L);
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
        assertThat(response.updatedByName()).isEqualTo("Chủ Nhà");
        assertThat(response.checkInTime()).isEqualTo(LocalTime.of(14, 0));
        // Mốc huỷ được sắp theo số giờ giảm dần
        assertThat(response.cancellationTiers())
                .extracting(tier -> tier.hoursBeforeCheckIn())
                .containsExactly(72, 24, 0);
    }

    @Test
    void mocHuy_qua3Moc_biChan() {
        // AC2: tối đa 3 mốc
        List<CancellationTierRequest> tiers = List.of(tier(96, 100), tier(72, 80), tier(24, 50), tier(0, 0));

        assertThatThrownBy(() -> OperatingSettingsService.validateCancellationTiers(tiers))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining("từ 1 đến 3 mốc");
    }

    @Test
    void mocHuy_trungSoGio_biChanVaBaoChongLan() {
        // AC3: nhập chồng lấn bị chặn
        List<CancellationTierRequest> tiers = List.of(tier(24, 100), tier(24, 50));

        assertThatThrownBy(() -> OperatingSettingsService.validateCancellationTiers(tiers))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining("chồng lấn");
    }

    @Test
    void mocHuy_hoanCocKhongGiamDan_biChan() {
        // AC3: càng sát ngày nhận phòng thì hoàn cọc phải càng thấp
        List<CancellationTierRequest> tiers = List.of(tier(72, 50), tier(24, 80));

        assertThatThrownBy(() -> OperatingSettingsService.validateCancellationTiers(tiers))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining("phải hoàn ít hơn");
    }

    @Test
    void gioTraPhongKhongTruocGioNhanPhong_biChan() {
        assertThatThrownBy(() -> settingsService.update(
                request(LocalTime.of(12, 0), LocalTime.of(14, 0), List.of(tier(24, 50))), 5L, "Chủ Nhà"))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining("phải trước giờ nhận phòng");
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void thamSoCoHieuLuc_layDungPhienBanTaiThoiDiemTaoBooking() {
        // AC4: booking tạo lúc nào thì dùng tham số có hiệu lực lúc đó
        OffsetDateTime bookingCreatedAt = OffsetDateTime.parse("2026-10-01T09:00:00+07:00");
        OperatingSettings oldVersion = new OperatingSettings();
        oldVersion.setCheckInTime(LocalTime.of(14, 0));
        when(settingsRepository.findFirstByCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(bookingCreatedAt))
                .thenReturn(Optional.of(oldVersion));

        OperatingSettings effective = settingsService.findEffectiveAt(bookingCreatedAt);

        assertThat(effective).isSameAs(oldVersion);
    }

    private static CancellationTierRequest tier(int hours, int percent) {
        return new CancellationTierRequest(hours, percent);
    }

    private static OperatingSettingsRequest request(
        LocalTime checkIn,
        LocalTime checkOut,
        List<CancellationTierRequest> tiers) {

    return new OperatingSettingsRequest(
            "HomeStay Test",
            "Hà Nội",
            "0912345678",
            "homestay@test.local",
            checkIn,
            checkOut,
            100_000L,
            200_000L,
            tiers,
            List.of("FRIDAY", "SATURDAY")
    );
}
}