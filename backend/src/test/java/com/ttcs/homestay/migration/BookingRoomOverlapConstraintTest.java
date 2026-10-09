package com.ttcs.homestay.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * S3-02 Lát 1: ràng buộc chống trùng phòng chạy trên PostgreSQL thật (H2 không có ràng buộc loại trừ).
 * Test tạo một schema tạm, chạy toàn bộ migration rồi xoá schema khi xong.
 * Chỉ chạy khi có biến môi trường HOMESTAY_PG_TEST_URL, HOMESTAY_PG_TEST_USER, HOMESTAY_PG_TEST_PASSWORD,
 * ví dụ HOMESTAY_PG_TEST_URL=jdbc:postgresql://localhost:5432/homestay_db.
 */
@EnabledIfEnvironmentVariable(named = "HOMESTAY_PG_TEST_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingRoomOverlapConstraintTest {

    private static final String OVERLAP_SQL_STATE = "23P01";

    private final String url = System.getenv("HOMESTAY_PG_TEST_URL");
    private final String user = System.getenv("HOMESTAY_PG_TEST_USER");
    private final String password = System.getenv("HOMESTAY_PG_TEST_PASSWORD");
    private final String schema = "s302_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private final AtomicInteger sequence = new AtomicInteger();

    private Connection connection;
    private long roomTypeId;
    private String roomTypeName;

    // Booking cũ tạo trước khi có migration V36, dùng để kiểm tra việc gán phòng cho dữ liệu hiện có.
    private long legacyFirst;
    private long legacySecond;
    private long legacyOverbooked;
    private long legacyExpiredHold;
    private long legacyRoomA;

    @BeforeAll
    void migrate() throws SQLException {
        connection = DriverManager.getConnection(url, user, password);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("SET search_path TO " + schema + ", public");
        }

        flyway("35").migrate();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT id, name FROM room_types ORDER BY id LIMIT 1")) {
            rs.next();
            roomTypeId = rs.getLong("id");
            roomTypeName = rs.getString("name");
        }
        // Loại phòng có đúng 2 phòng hoạt động cho dữ liệu cũ: 3 booking cùng đêm, booking thứ 3 không còn phòng.
        try (Statement statement = connection.createStatement()) {
            statement.execute("UPDATE rooms SET active = FALSE WHERE lower(room_type) = lower('"
                    + roomTypeName.replace("'", "''") + "')");
        }
        legacyRoomA = room();
        room();
        LocalDate in = LocalDate.of(2030, 1, 10);
        LocalDate out = LocalDate.of(2030, 1, 12);
        legacyFirst = legacyBooking("DA_XAC_NHAN", in, out, "2030-01-01T08:00:00Z", null);
        legacySecond = legacyBooking("CHO_XAC_NHAN", in, out, "2030-01-01T09:00:00Z", "2999-01-01T00:00:00Z");
        legacyOverbooked = legacyBooking("CHO_XAC_NHAN", in, out, "2030-01-01T10:00:00Z", "2999-01-01T00:00:00Z");
        legacyExpiredHold = legacyBooking("CHO_XAC_NHAN", in, out, "2030-01-01T07:00:00Z", "2020-01-01T00:00:00Z");

        flyway(null).migrate();
    }

    @AfterAll
    void dropSchema() throws SQLException {
        if (connection == null) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        } finally {
            connection.close();
        }
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(url, user, password)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    @Test
    void migration_ganPhongChoBookingCuTheoThuTuTaoVaDeTrongBookingKhongConPhong() throws SQLException {
        assertThat(statusOf(legacyExpiredHold)).isEqualTo("DA_HET_HAN");
        assertThat(roomOf(legacyExpiredHold)).isNull();
        assertThat(roomOf(legacyFirst)).isEqualTo(legacyRoomA);
        assertThat(roomOf(legacySecond)).isNotNull().isNotEqualTo(legacyRoomA);
        assertThat(roomOf(legacyOverbooked)).isNull();
    }

    @Test
    void haiBookingTrungMotDemCungPhong_biCoSoDuLieuTuChoi() throws SQLException {
        long room = room();
        booking(room, "DA_XAC_NHAN", LocalDate.of(2031, 3, 20), LocalDate.of(2031, 3, 23));

        assertThatThrownBy(() -> booking(room, "CHO_XAC_NHAN", LocalDate.of(2031, 3, 22), LocalDate.of(2031, 3, 24)))
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(OVERLAP_SQL_STATE));
    }

    @Test
    void traPhongNgay23VaNhanPhongNgay23CungPhong_hopLe() throws SQLException {
        long room = room();
        booking(room, "DA_XAC_NHAN", LocalDate.of(2031, 4, 20), LocalDate.of(2031, 4, 23));
        booking(room, "CHO_XAC_NHAN", LocalDate.of(2031, 4, 23), LocalDate.of(2031, 4, 25));

        assertThat(countOn(room)).isEqualTo(2);
    }

    @Test
    void haiPhongKhacNhauCungDem_hopLe() throws SQLException {
        long roomA = room();
        long roomB = room();
        booking(roomA, "DA_XAC_NHAN", LocalDate.of(2031, 5, 20), LocalDate.of(2031, 5, 23));
        booking(roomB, "DA_XAC_NHAN", LocalDate.of(2031, 5, 20), LocalDate.of(2031, 5, 23));

        assertThat(countOn(roomA) + countOn(roomB)).isEqualTo(2);
    }

    @Test
    void bookingDaHuyHetHanHoacDaTraPhong_khongChiemPhong() throws SQLException {
        long room = room();
        LocalDate in = LocalDate.of(2031, 6, 20);
        LocalDate out = LocalDate.of(2031, 6, 23);
        booking(room, "DA_HUY", in, out);
        booking(room, "DA_HET_HAN", in, out);
        booking(room, "DA_TRA_PHONG", in, out);
        booking(room, "DA_XAC_NHAN", in, out);

        assertThat(countOn(room)).isEqualTo(4);
    }

    @Test
    void khoiPhucBookingDaHuyKhiPhongDaCoNguoi_biCoSoDuLieuTuChoi() throws SQLException {
        long room = room();
        LocalDate in = LocalDate.of(2031, 7, 20);
        LocalDate out = LocalDate.of(2031, 7, 23);
        long cancelled = booking(room, "DA_HUY", in, out);
        booking(room, "DA_XAC_NHAN", in, out);

        assertThatThrownBy(() -> execute("UPDATE bookings SET status = 'CHO_XAC_NHAN' WHERE id = " + cancelled))
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(OVERLAP_SQL_STATE));
    }

    private long room() throws SQLException {
        String number = "IT-" + sequence.incrementAndGet();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO rooms (room_number, floor, room_type, status, active) VALUES (?, 9, ?, 'TRONG_SACH', TRUE)"
                        + " RETURNING id")) {
            statement.setString(1, number);
            statement.setString(2, roomTypeName);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long booking(long roomId, String status, LocalDate checkIn, LocalDate checkOut) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bookings (room_type_id, room_type_name_snapshot, check_in_date, check_out_date,"
                        + " weekday_price_snapshot, weekend_price_snapshot, weekend_days_snapshot, total_amount,"
                        + " booking_code, guest_name, status, room_id)"
                        + " VALUES (?, ?, ?, ?, 500000, 600000, 'SATURDAY', 1500000, ?, 'Khách thử', ?, ?) RETURNING id")) {
            statement.setLong(1, roomTypeId);
            statement.setString(2, roomTypeName);
            statement.setObject(3, checkIn);
            statement.setObject(4, checkOut);
            statement.setString(5, "IT" + sequence.incrementAndGet());
            statement.setString(6, status);
            statement.setLong(7, roomId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long legacyBooking(String status, LocalDate checkIn, LocalDate checkOut, String createdAt, String holdExpiresAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bookings (room_type_id, room_type_name_snapshot, check_in_date, check_out_date,"
                        + " weekday_price_snapshot, weekend_price_snapshot, weekend_days_snapshot, total_amount,"
                        + " booking_code, guest_name, status, created_at, hold_expires_at)"
                        + " VALUES (?, ?, ?, ?, 500000, 600000, 'SATURDAY', 1000000, ?, 'Khách cũ', ?,"
                        + " ?::timestamptz, ?::timestamptz) RETURNING id")) {
            statement.setLong(1, roomTypeId);
            statement.setString(2, roomTypeName);
            statement.setObject(3, checkIn);
            statement.setObject(4, checkOut);
            statement.setString(5, "OLD" + sequence.incrementAndGet());
            statement.setString(6, status);
            statement.setString(7, createdAt);
            statement.setString(8, holdExpiresAt);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private int countOn(long roomId) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT count(*) FROM bookings WHERE room_id = " + roomId)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Long roomOf(long bookingId) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT room_id FROM bookings WHERE id = " + bookingId)) {
            rs.next();
            long value = rs.getLong(1);
            return rs.wasNull() ? null : value;
        }
    }

    private String statusOf(long bookingId) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT status FROM bookings WHERE id = " + bookingId)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
