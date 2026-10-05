package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S2-08 Lát 2: tối đa 10 lần tra cứu sai từ một IP trong 15 phút. */
class BookingLookupRateLimiterTest {

    private static final String IP = "203.0.113.10";
    private static final Instant START = Instant.parse("2026-10-05T03:00:00Z");

    /** Đồng hồ giả, chỉnh được thời gian để không phải chờ 15 phút thật. */
    private static final class MutableClock extends Clock {
        private Instant now = START;

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private MutableClock clock;
    private BookingLookupRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new BookingLookupRateLimiter(clock);
    }

    private void fail(int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(IP);
        }
    }

    @Test
    void sai9Lan_vanDuocTraCuu() {
        fail(9);

        assertThat(limiter.blockedUntil(IP)).isEmpty();
    }

    @Test
    void sai10Lan_biChanDen15PhutSauLanSaiDauTien() {
        fail(1);
        clock.advance(Duration.ofMinutes(2));
        fail(9);

        assertThat(limiter.blockedUntil(IP)).contains(START.plus(Duration.ofMinutes(15)));
    }

    @Test
    void ipKhac_khongBiAnhHuong() {
        fail(10);

        assertThat(limiter.blockedUntil("198.51.100.7")).isEmpty();
    }

    @Test
    void het15Phut_duocTraCuuLai() {
        fail(10);
        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(limiter.blockedUntil(IP)).isPresent();

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.blockedUntil(IP)).isEmpty();
    }

    @Test
    void lanSaiCuRaKhoiKhoang15Phut_khongConDuocTinh() {
        fail(5);
        clock.advance(Duration.ofMinutes(10));
        fail(4);
        clock.advance(Duration.ofMinutes(6));
        fail(1);

        // 5 lần đầu đã quá 15 phút, chỉ còn 5 lần sai trong khoảng
        assertThat(limiter.blockedUntil(IP)).isEmpty();
    }

    @Test
    void khongCoDiaChiIp_khongGioiHan() {
        for (int i = 0; i < 20; i++) {
            limiter.recordFailure(null);
        }

        assertThat(limiter.blockedUntil(null)).isEmpty();
    }
}