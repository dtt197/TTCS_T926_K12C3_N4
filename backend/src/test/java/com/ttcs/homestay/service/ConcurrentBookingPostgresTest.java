package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcs.homestay.dto.booking.GuestBookingRequest;
import com.ttcs.homestay.exception.RoomUnavailableException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * S3-03 Lượt 1: nhiều khách cùng giữ suất cuối cùng, chạy trên PostgreSQL thật.
 * Chỉ chạy khi có HOMESTAY_PG_TEST_URL, HOMESTAY_PG_TEST_USER, HOMESTAY_PG_TEST_PASSWORD.
 * Dữ liệu nằm trong một schema tạm và được xoá khi chạy xong.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HOMESTAY_PG_TEST_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrentBookingPostgresTest {

    private static final String SCHEMA =
            "s302_race_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String OCCUPYING = "('CHO_XAC_NHAN', 'DA_XAC_NHAN', 'DA_NHAN_PHONG')";
    private static final ZoneId HOMESTAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv("HOMESTAY_PG_TEST_URL") + "?currentSchema=" + SCHEMA + ",public");
        registry.add("spring.datasource.username", () -> System.getenv("HOMESTAY_PG_TEST_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("HOMESTAY_PG_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired
    private GuestBookingService guestBookingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterAll
    void dropSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void haiYeuCauDongThoiChoPhongCuoiCung_dungMotYeuCauThanhCong() throws InterruptedException {
        long roomTypeId = prepareRoomType("S203_DLX", 1);
        LocalDate checkIn = LocalDate.now(HOMESTAY_ZONE).plusDays(20);

        Result result = race(roomTypeId, 2, checkIn, checkIn.plusDays(1));

        assertThat(result.otherErrors()).isEmpty();
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.soldOut()).isEqualTo(1);
        assertThat(bookingsFrom(roomTypeId, checkIn)).isEqualTo(1);
    }

    @Test
    void haiTramYeuCauSongSongChoLoaiPhongCon3Phong_dung3BookingVaKhongTrung() throws InterruptedException {
        long roomTypeId = prepareRoomType("S203_STD", 3);
        LocalDate checkIn = LocalDate.now(HOMESTAY_ZONE).plusDays(25);

        Result result = race(roomTypeId, 200, checkIn, checkIn.plusDays(2));

        System.out.printf("%n[S3-02] 200 luot dat song song: thanh cong %d, het phong (409) %d,"
                        + " loi khac %d, cap booking trung phong %d, so phong duoc dung %d%n",
                result.created(), result.soldOut(), result.otherErrors().size(),
                overlappingPairs(roomTypeId), distinctRooms(roomTypeId, checkIn));
        assertThat(result.otherErrors()).isEmpty();
        assertThat(result.created()).isEqualTo(3);
        assertThat(result.soldOut()).isEqualTo(197);
        assertThat(bookingsFrom(roomTypeId, checkIn)).isEqualTo(3);
        assertThat(distinctRooms(roomTypeId, checkIn)).isZero();
        assertThat(overlappingPairs(roomTypeId)).isZero();
    }

    /** Dùng loại phòng mẫu có sẵn giá (migration V28), tắt phòng cũ và tạo đúng số phòng cần thử. */
    private long prepareRoomType(String code, int rooms) {
        Long roomTypeId = jdbcTemplate.queryForObject("SELECT id FROM room_types WHERE code = ?", Long.class, code);
        String name = jdbcTemplate.queryForObject("SELECT name FROM room_types WHERE id = ?", String.class, roomTypeId);
        jdbcTemplate.update("UPDATE room_types SET status = TRUE WHERE id = ?", roomTypeId);
        jdbcTemplate.update("UPDATE rooms SET active = FALSE WHERE lower(room_type) = lower(?)", name);
        for (int i = 1; i <= rooms; i++) {
            jdbcTemplate.update(
                    "INSERT INTO rooms (room_number, floor, room_type, status, active) VALUES (?, 9, ?, 'TRONG_SACH', TRUE)",
                    code + "-" + i, name);
        }
        return roomTypeId;
    }

    private record Result(int created, int soldOut, List<String> otherErrors) {}

    /** Gửi cùng lúc nhiều yêu cầu đặt cho cùng loại phòng và khoảng ngày. */
    private Result race(long roomTypeId, int requests, LocalDate checkIn, LocalDate checkOut)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        Queue<String> otherErrors = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < requests; i++) {
            int guest = i;
            pool.submit(() -> {
                try {
                    start.await();
                    guestBookingService.createGuestBooking(new GuestBookingRequest(
                            roomTypeId, checkIn, checkOut, "Khach thu " + guest, "0912345678",
                            "khach" + guest + "@example.test", 1, null, true));
                    created.incrementAndGet();
                } catch (RoomUnavailableException exception) {
                    soldOut.incrementAndGet();
                } catch (Exception exception) {
                    otherErrors.add(exception.toString());
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(2, TimeUnit.MINUTES)).isTrue();
        return new Result(created.get(), soldOut.get(), List.copyOf(otherErrors));
    }

    private int bookingsFrom(long roomTypeId, LocalDate checkIn) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings WHERE room_type_id = ? AND check_in_date = ?",
                Integer.class, roomTypeId, checkIn);
    }

    private int distinctRooms(long roomTypeId, LocalDate checkIn) {
        return jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT room_id) FROM bookings WHERE room_type_id = ? AND check_in_date = ?",
                Integer.class, roomTypeId, checkIn);
    }

    private int overlappingPairs(long roomTypeId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings a JOIN bookings b ON a.room_id = b.room_id AND a.id < b.id"
                        + " AND a.check_in_date < b.check_out_date AND b.check_in_date < a.check_out_date"
                        + " WHERE a.room_type_id = ? AND a.status IN " + OCCUPYING + " AND b.status IN " + OCCUPYING,
                Integer.class, roomTypeId);
    }
}
