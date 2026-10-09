package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcs.homestay.entity.OperatingSettings;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CancellationRefundCalculatorTest {

    private static OperatingSettings settings() {
        OperatingSettings s = new OperatingSettings();
        s.addCancellationTier(24, 50);
        s.addCancellationTier(72, 100);
        s.addCancellationTier(6, 20);
        return s;
    }

    private static CancellationRefundCalculator.Result calc(long hours, long deposit) {
        return CancellationRefundCalculator.calculate(settings().getCancellationTiers(), hours, deposit);
    }

    @Test
    void dungTaiRanhGioiHoanDayDu() {
        assertThat(calc(72, 1_000_000).refundPercent()).isEqualTo(100);
        assertThat(calc(72, 1_000_000).refundAmount()).isEqualTo(1_000_000);
        assertThat(calc(100, 1_000_000).refundPercent()).isEqualTo(100);
    }

    @Test
    void duoiMocTrenMotGioThiXuongMocThap() {
        assertThat(calc(71, 1_000_000).refundPercent()).isEqualTo(50);
        assertThat(calc(24, 1_000_000).refundPercent()).isEqualTo(50);
        assertThat(calc(23, 1_000_000).refundPercent()).isEqualTo(20);
        assertThat(calc(6, 1_000_000).refundPercent()).isEqualTo(20);
    }

    @Test
    void duoiMocThapNhatThiKhongHoan() {
        assertThat(calc(5, 1_000_000).refundPercent()).isZero();
        assertThat(calc(5, 1_000_000).refundAmount()).isZero();
        assertThat(calc(-1, 1_000_000).refundPercent()).isZero();
    }

    @Test
    void lamTronXuongDenDong() {
        assertThat(calc(30, 1_000_001).refundAmount()).isEqualTo(500_000);
    }

    @Test
    void khongCoTienCocThiHoanBangKhong() {
        assertThat(calc(80, 0).refundPercent()).isEqualTo(100);
        assertThat(calc(80, 0).refundAmount()).isZero();
    }

    @Test
    void khongCoMocNaoThiKhongHoan() {
        var result = CancellationRefundCalculator.calculate(new OperatingSettings().getCancellationTiers(), 500, 1_000_000);
        assertThat(result.refundPercent()).isZero();
        assertThat(result.refundAmount()).isZero();
    }

    @Test
    void thayDoiMocThiKetQuaDoiTheo() {
        OperatingSettings s = new OperatingSettings();
        s.addCancellationTier(48, 80);
        var result = CancellationRefundCalculator.calculate(s.getCancellationTiers(), 50, 1_000_000);
        assertThat(result.refundPercent()).isEqualTo(80);
        assertThat(result.refundAmount()).isEqualTo(800_000);
    }

    @Test
    void tinhSoGioConLaiDenGioNhanPhong() {
        LocalDate checkIn = LocalDate.of(2026, 10, 20);
        LocalTime checkInTime = LocalTime.of(14, 0);
        ZoneOffset vn = ZoneOffset.ofHours(7);

        assertThat(CancellationRefundCalculator.hoursUntilCheckIn(
                OffsetDateTime.of(2026, 10, 17, 14, 0, 0, 0, vn), checkIn, checkInTime)).isEqualTo(72);
        assertThat(CancellationRefundCalculator.hoursUntilCheckIn(
                OffsetDateTime.of(2026, 10, 17, 14, 1, 0, 0, vn), checkIn, checkInTime)).isEqualTo(71);
        assertThat(CancellationRefundCalculator.hoursUntilCheckIn(
                OffsetDateTime.of(2026, 10, 20, 14, 1, 0, 0, vn), checkIn, checkInTime)).isEqualTo(-1);
    }
}