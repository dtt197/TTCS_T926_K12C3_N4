package com.ttcs.homestay.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S4-09: Nạp bộ dữ liệu mẫu về trạng thái ban đầu.
 * <p>
 * Thực hiện truncate và tái tạo:
 * - 4 loại phòng (DON, DOI, GIA_DINH, STUDIO_ADULT)
 * - 20 phòng phân bố đều
 * - Bảng giá ngày thường, cuối tuần và ngày lễ (price_overrides)
 * - 30 booking phân bố trên 3 tháng (tháng trước, hiện tại, tháng sau)
 * </p>
 */
@Service
public class SampleDataService {

    private final JdbcTemplate jdbcTemplate;

    public SampleDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Nạp lại toàn bộ dữ liệu mẫu về trạng thái ban đầu.
     * Xoá và tạo lại: bookings, price_overrides, rooms, room_types.
     */
    @Transactional
    public SampleDataSummary reload() {
        // ── 1. Xoá dữ liệu phụ thuộc trước (thứ tự quan trọng) ──────────────
        truncateDependentTables();

        // ── 2. Nạp loại phòng ────────────────────────────────────────────────
        int roomTypeCount = seedRoomTypes();

        // ── 3. Nạp phòng ─────────────────────────────────────────────────────
        int roomCount = seedRooms();

        // ── 4. Nạp bảng giá ngày lễ (price_overrides) ───────────────────────
        int priceOverrideCount = seedPriceOverrides();

        // ── 5. Nạp booking ───────────────────────────────────────────────────
        int bookingCount = seedBookings();

        return new SampleDataSummary(roomTypeCount, roomCount, priceOverrideCount, bookingCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Xoá dữ liệu cũ
    // ─────────────────────────────────────────────────────────────────────────

    private void truncateDependentTables() {
        // Xoá theo thứ tự FK: con trước, cha sau
        jdbcTemplate.execute("DELETE FROM booking_nightly_price_snapshots");
        jdbcTemplate.execute("DELETE FROM booking_guests");
        jdbcTemplate.execute("DELETE FROM booking_surcharges");
        jdbcTemplate.execute("DELETE FROM booking_deposit_adjustments");
        jdbcTemplate.execute("DELETE FROM booking_deposits");
        jdbcTemplate.execute("DELETE FROM booking_audit_logs");
        jdbcTemplate.execute("DELETE FROM booking_room_change_history");
        jdbcTemplate.execute("DELETE FROM booking_settlements");
        jdbcTemplate.execute("DELETE FROM bookings");
        jdbcTemplate.execute("DELETE FROM price_overrides");
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM maintenance_request_history");
        jdbcTemplate.execute("DELETE FROM maintenance_requests");
        jdbcTemplate.execute("DELETE FROM room_status_history");
        jdbcTemplate.execute("DELETE FROM check_ins");
        jdbcTemplate.execute("DELETE FROM rooms");
        jdbcTemplate.execute("DELETE FROM room_type_cancellation_tiers");
        jdbcTemplate.execute("DELETE FROM room_type_amenities");
        jdbcTemplate.execute("DELETE FROM room_type_images");
        jdbcTemplate.execute("DELETE FROM room_types");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Loại phòng
    // ─────────────────────────────────────────────────────────────────────────

    private int seedRoomTypes() {
        jdbcTemplate.update("""
            INSERT INTO room_types (
                code, name, standard_capacity, max_capacity, number_of_beds,
                description, status,
                weekday_price, weekend_price,
                check_in_time, check_out_time,
                allow_children, child_policy,
                extra_person_fee, extra_guest_fee,
                cancellation_policy,
                image_url, image_alt
            ) VALUES
            ('DON', 'Phòng đơn', 1, 2, 1,
             'Phòng đơn ấm cúng, thiết kế tối giản tinh tế với bàn làm việc và cửa sổ hướng vườn.',
             TRUE, 300000, 400000, '14:00', '12:00',
             TRUE, 'Cho phép mang theo trẻ nhỏ. Trẻ dưới 6 tuổi ở cùng người lớn miễn phí.',
             150000, 150000, 'Chính sách hủy linh hoạt chuẩn homestay',
             NULL, 'Phòng đơn'),
            ('DOI', 'Phòng đôi', 2, 3, 1,
             'Phòng đôi sang trọng với giường Queen êm ái, ánh sáng tự nhiên ngập tràn và không gian thư giãn hoàn hảo.',
             TRUE, 500000, 650000, '14:00', '12:00',
             TRUE, 'Cho phép tối đa 1 trẻ em dưới 6 tuổi ngủ chung giường với bố mẹ miễn phí.',
             200000, 200000, 'Chính sách hủy tiêu chuẩn hoàn 100% trước 72 giờ',
             NULL, 'Phòng đôi'),
            ('GIA_DINH', 'Phòng gia đình', 4, 5, 2,
             'Phòng gia đình rộng rãi gồm 2 giường đôi lớn, ban công thoáng mát và khu vực sinh hoạt chung ấm cúng.',
             TRUE, 900000, 1200000, '14:00', '12:00',
             TRUE, 'Rất hoan nghênh gia đình có trẻ nhỏ. Hỗ trợ miễn phí cũi em bé nếu đặt trước.',
             250000, 250000, 'Hủy miễn phí trước 72h cho gia đình',
             NULL, 'Phòng gia đình'),
            ('STUDIO_ADULT', 'Studio Người Lớn', 2, 2, 1,
             'Không gian nghỉ dưỡng biệt lập cao cấp dành riêng cho các cặp đôi hoặc cá nhân cần sự yên tĩnh.',
             TRUE, 700000, 900000, '15:00', '11:00',
             FALSE, 'Không cho phép mang theo trẻ nhỏ để đảm bảo không gian nghỉ dưỡng yên tĩnh tuyệt đối.',
             300000, 300000, 'Chính sách hủy nghiêm ngặt dành riêng cho phòng Studio',
             NULL, 'Studio Người Lớn')
            """);

        // Cancellation tiers cho STUDIO_ADULT
        Long studioId = jdbcTemplate.queryForObject(
                "SELECT id FROM room_types WHERE code = 'STUDIO_ADULT'", Long.class);
        if (studioId != null) {
            jdbcTemplate.update("""
                INSERT INTO room_type_cancellation_tiers (room_type_id, hours_before_check_in, refund_percent)
                VALUES (?, 48, 80), (?, 0, 0)
                """, studioId, studioId);
        }

        return 4;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phòng (20 phòng)
    // ─────────────────────────────────────────────────────────────────────────

    private int seedRooms() {
        // Phân bố: DON × 5, DOI × 7, GIA_DINH × 5, STUDIO_ADULT × 3 = 20
        jdbcTemplate.update("""
            INSERT INTO rooms (room_number, floor, room_type, status, active)
            VALUES
            -- Tầng 1: Phòng đơn
            ('101', 1, 'Phòng đơn',      'TRONG_SACH', TRUE),
            ('102', 1, 'Phòng đơn',      'TRONG_SACH', TRUE),
            -- Tầng 1: Phòng đôi
            ('103', 1, 'Phòng đôi',      'TRONG_SACH', TRUE),
            ('104', 1, 'Phòng đôi',      'TRONG_SACH', TRUE),
            ('105', 1, 'Phòng đôi',      'TRONG_BAN',  TRUE),
            -- Tầng 2: Phòng đơn
            ('201', 2, 'Phòng đơn',      'TRONG_SACH', TRUE),
            ('202', 2, 'Phòng đơn',      'TRONG_SACH', TRUE),
            ('203', 2, 'Phòng đơn',      'TRONG_BAN',  TRUE),
            -- Tầng 2: Phòng đôi
            ('204', 2, 'Phòng đôi',      'DANG_O',     TRUE),
            ('205', 2, 'Phòng đôi',      'TRONG_SACH', TRUE),
            ('206', 2, 'Phòng đôi',      'TRONG_SACH', TRUE),
            ('207', 2, 'Phòng đôi',      'TRONG_SACH', TRUE),
            -- Tầng 3: Phòng gia đình
            ('301', 3, 'Phòng gia đình', 'TRONG_SACH', TRUE),
            ('302', 3, 'Phòng gia đình', 'DANG_O',     TRUE),
            ('303', 3, 'Phòng gia đình', 'TRONG_SACH', TRUE),
            ('304', 3, 'Phòng gia đình', 'TRONG_BAN',  TRUE),
            ('305', 3, 'Phòng gia đình', 'TRONG_SACH', TRUE),
            -- Tầng 4: Studio Người Lớn
            ('401', 4, 'Studio Người Lớn', 'TRONG_SACH', TRUE),
            ('402', 4, 'Studio Người Lớn', 'DANG_O',     TRUE),
            ('403', 4, 'Studio Người Lớn', 'TRONG_SACH', TRUE)
            """);
        return 20;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bảng giá ngày lễ (price_overrides)
    // ─────────────────────────────────────────────────────────────────────────

    private int seedPriceOverrides() {
        // Lấy năm hiện tại để tạo giá ngày lễ có liên quan
        int year = LocalDate.now().getYear();
        OffsetDateTime now = OffsetDateTime.now();

        List<Long> roomTypeIds = jdbcTemplate.queryForList(
                "SELECT id FROM room_types WHERE code IN ('DON','DOI','GIA_DINH','STUDIO_ADULT') ORDER BY code",
                Long.class);

        if (roomTypeIds.isEmpty()) return 0;

        // Giả định thứ tự: DOI, DON, GIA_DINH, STUDIO_ADULT (sắp xếp theo code)
        Long doiId = getRoomTypeId("DOI");
        Long donId = getRoomTypeId("DON");
        Long giaId = getRoomTypeId("GIA_DINH");
        Long stuId = getRoomTypeId("STUDIO_ADULT");

        int count = 0;

        // Tết Dương lịch 01/01
        count += insertPriceOverride("Tết Dương lịch " + year,
                donId, LocalDate.of(year, 1, 1), LocalDate.of(year, 1, 3), 450000, now);
        count += insertPriceOverride("Tết Dương lịch " + year,
                doiId, LocalDate.of(year, 1, 1), LocalDate.of(year, 1, 3), 750000, now);
        count += insertPriceOverride("Tết Dương lịch " + year,
                giaId, LocalDate.of(year, 1, 1), LocalDate.of(year, 1, 3), 1400000, now);
        count += insertPriceOverride("Tết Dương lịch " + year,
                stuId, LocalDate.of(year, 1, 1), LocalDate.of(year, 1, 3), 1050000, now);

        // Lễ 30/4 - 01/05
        count += insertPriceOverride("Lễ 30/4 - 1/5 " + year,
                donId, LocalDate.of(year, 4, 28), LocalDate.of(year, 5, 2), 480000, now);
        count += insertPriceOverride("Lễ 30/4 - 1/5 " + year,
                doiId, LocalDate.of(year, 4, 28), LocalDate.of(year, 5, 2), 800000, now);
        count += insertPriceOverride("Lễ 30/4 - 1/5 " + year,
                giaId, LocalDate.of(year, 4, 28), LocalDate.of(year, 5, 2), 1500000, now);
        count += insertPriceOverride("Lễ 30/4 - 1/5 " + year,
                stuId, LocalDate.of(year, 4, 28), LocalDate.of(year, 5, 2), 1100000, now);

        // Quốc khánh 2/9
        count += insertPriceOverride("Quốc khánh 2/9 " + year,
                donId, LocalDate.of(year, 9, 1), LocalDate.of(year, 9, 3), 460000, now);
        count += insertPriceOverride("Quốc khánh 2/9 " + year,
                doiId, LocalDate.of(year, 9, 1), LocalDate.of(year, 9, 3), 780000, now);
        count += insertPriceOverride("Quốc khánh 2/9 " + year,
                giaId, LocalDate.of(year, 9, 1), LocalDate.of(year, 9, 3), 1450000, now);
        count += insertPriceOverride("Quốc khánh 2/9 " + year,
                stuId, LocalDate.of(year, 9, 1), LocalDate.of(year, 9, 3), 1050000, now);

        // Giáng sinh + Năm mới (tháng 12)
        count += insertPriceOverride("Giáng sinh & Năm mới " + year,
                donId, LocalDate.of(year, 12, 24), LocalDate.of(year, 12, 31), 500000, now);
        count += insertPriceOverride("Giáng sinh & Năm mới " + year,
                doiId, LocalDate.of(year, 12, 24), LocalDate.of(year, 12, 31), 850000, now);
        count += insertPriceOverride("Giáng sinh & Năm mới " + year,
                giaId, LocalDate.of(year, 12, 24), LocalDate.of(year, 12, 31), 1600000, now);
        count += insertPriceOverride("Giáng sinh & Năm mới " + year,
                stuId, LocalDate.of(year, 12, 24), LocalDate.of(year, 12, 31), 1200000, now);

        return count;
    }

    private int insertPriceOverride(String name, Long roomTypeId,
            LocalDate start, LocalDate end, long price, OffsetDateTime now) {
        if (roomTypeId == null) return 0;
        jdbcTemplate.update("""
            INSERT INTO price_overrides (name, room_type_id, start_date, end_date, price_per_night, created_by_name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'Hệ thống - Dữ liệu mẫu S4-09', ?, ?)
            """, name, roomTypeId, start, end, price, now, now);
        return 1;
    }

    private Long getRoomTypeId(String code) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM room_types WHERE code = ?", Long.class, code);
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Booking (30 booking phân bố trên 3 tháng)
    // ─────────────────────────────────────────────────────────────────────────

    private int seedBookings() {
        Long doiId  = getRoomTypeId("DOI");
        Long donId  = getRoomTypeId("DON");
        Long giaId  = getRoomTypeId("GIA_DINH");
        Long stuId  = getRoomTypeId("STUDIO_ADULT");

        LocalDate today = LocalDate.now();

        // ── Tháng trước: 10 booking ──────────────────────────────────────────
        LocalDate pm = today.minusMonths(1).withDayOfMonth(1);

        insertBooking("BK-S4-001", "Nguyễn Văn An",      "0912345601", "an.nguyen@example.com",    2, doiId, "Phòng đôi",        pm.plusDays(1),  pm.plusDays(3),  500000, 650000, 2);
        insertBooking("BK-S4-002", "Trần Thị Bình",      "0912345602", "binh.tran@example.com",    1, donId, "Phòng đơn",        pm.plusDays(2),  pm.plusDays(4),  300000, 400000, 1);
        insertBooking("BK-S4-003", "Lê Hoàng Minh",      "0912345603", "minh.le@example.com",      4, giaId, "Phòng gia đình",   pm.plusDays(5),  pm.plusDays(8),  900000, 1200000, 4);
        insertBooking("BK-S4-004", "Phạm Thanh Hương",   "0912345604", "huong.pham@example.com",   2, stuId, "Studio Người Lớn", pm.plusDays(6),  pm.plusDays(9),  700000, 900000,  2);
        insertBooking("BK-S4-005", "Vũ Đức Long",        "0912345605", "long.vu@example.com",      2, doiId, "Phòng đôi",        pm.plusDays(10), pm.plusDays(12), 500000, 650000,  2);
        insertBooking("BK-S4-006", "Đặng Thu Trang",     "0912345606", "trang.dang@example.com",   1, donId, "Phòng đơn",        pm.plusDays(12), pm.plusDays(14), 300000, 400000,  1);
        insertBooking("BK-S4-007", "Bùi Quang Huy",      "0912345607", "huy.bui@example.com",      3, giaId, "Phòng gia đình",   pm.plusDays(14), pm.plusDays(17), 900000, 1200000, 3);
        insertBooking("BK-S4-008", "Hoàng Minh Châu",    "0912345608", "chau.hoang@example.com",   2, doiId, "Phòng đôi",        pm.plusDays(18), pm.plusDays(20), 500000, 650000,  2);
        insertBooking("BK-S4-009", "Ngô Thị Lan",        "0912345609", "lan.ngo@example.com",      2, stuId, "Studio Người Lớn", pm.plusDays(20), pm.plusDays(22), 700000, 900000,  2);
        insertBooking("BK-S4-010", "Trịnh Văn Khoa",     "0912345610", "khoa.trinh@example.com",   1, donId, "Phòng đơn",        pm.plusDays(22), pm.plusDays(25), 300000, 400000,  1);

        // ── Tháng hiện tại: 10 booking ───────────────────────────────────────
        LocalDate cm = today.withDayOfMonth(1);

        insertBooking("BK-S4-011", "Lý Thanh Thảo",      "0912345611", "thao.ly@example.com",      2, doiId, "Phòng đôi",        cm.plusDays(1),  cm.plusDays(4),  500000, 650000,  2);
        insertBooking("BK-S4-012", "Dương Anh Tú",       "0912345612", "tu.duong@example.com",     1, donId, "Phòng đơn",        cm.plusDays(3),  cm.plusDays(5),  300000, 400000,  1);
        insertBooking("BK-S4-013", "Đinh Thị Ngọc",      "0912345613", "ngoc.dinh@example.com",    4, giaId, "Phòng gia đình",   cm.plusDays(5),  cm.plusDays(9),  900000, 1200000, 4);
        insertBooking("BK-S4-014", "Lưu Bảo Nam",        "0912345614", "nam.luu@example.com",      2, stuId, "Studio Người Lớn", cm.plusDays(7),  cm.plusDays(10), 700000, 900000,  2);
        insertBooking("BK-S4-015", "Hà Phương Thùy",     "0912345615", "thuy.ha@example.com",      2, doiId, "Phòng đôi",        cm.plusDays(10), cm.plusDays(13), 500000, 650000,  2);
        insertBooking("BK-S4-016", "Tô Minh Đức",        "0912345616", "duc.to@example.com",       1, donId, "Phòng đơn",        cm.plusDays(12), cm.plusDays(14), 300000, 400000,  1);
        insertBooking("BK-S4-017", "Cao Thị Hoa",        "0912345617", "hoa.cao@example.com",      5, giaId, "Phòng gia đình",   cm.plusDays(14), cm.plusDays(18), 900000, 1200000, 5);
        insertBooking("BK-S4-018", "Phan Đình Toàn",     "0912345618", "toan.phan@example.com",    2, doiId, "Phòng đôi",        cm.plusDays(17), cm.plusDays(20), 500000, 650000,  2);
        insertBooking("BK-S4-019", "Mai Thị Thu",        "0912345619", "thu.mai@example.com",      2, stuId, "Studio Người Lớn", cm.plusDays(20), cm.plusDays(23), 700000, 900000,  2);
        insertBooking("BK-S4-020", "Đỗ Văn Tuyên",       "0912345620", "tuyen.do@example.com",     1, donId, "Phòng đơn",        cm.plusDays(23), cm.plusDays(26), 300000, 400000,  1);

        // ── Tháng tới: 10 booking ────────────────────────────────────────────
        LocalDate nm = today.plusMonths(1).withDayOfMonth(1);

        insertBooking("BK-S4-021", "Trương Công Danh",   "0912345621", "danh.truong@example.com",  2, doiId, "Phòng đôi",        nm.plusDays(1),  nm.plusDays(4),  500000, 650000,  2);
        insertBooking("BK-S4-022", "Kiều Thị Nga",       "0912345622", "nga.kieu@example.com",     1, donId, "Phòng đơn",        nm.plusDays(3),  nm.plusDays(6),  300000, 400000,  1);
        insertBooking("BK-S4-023", "Châu Thanh Phong",   "0912345623", "phong.chau@example.com",   4, giaId, "Phòng gia đình",   nm.plusDays(5),  nm.plusDays(9),  900000, 1200000, 4);
        insertBooking("BK-S4-024", "Lê Thị Ánh",         "0912345624", "anh.le@example.com",       2, stuId, "Studio Người Lớn", nm.plusDays(7),  nm.plusDays(10), 700000, 900000,  2);
        insertBooking("BK-S4-025", "Vương Hữu Phúc",     "0912345625", "phuc.vuong@example.com",   2, doiId, "Phòng đôi",        nm.plusDays(10), nm.plusDays(13), 500000, 650000,  2);
        insertBooking("BK-S4-026", "Tạ Thị Kim",         "0912345626", "kim.ta@example.com",       1, donId, "Phòng đơn",        nm.plusDays(12), nm.plusDays(15), 300000, 400000,  1);
        insertBooking("BK-S4-027", "Hồ Trung Hiếu",      "0912345627", "hieu.ho@example.com",      3, giaId, "Phòng gia đình",   nm.plusDays(14), nm.plusDays(18), 900000, 1200000, 3);
        insertBooking("BK-S4-028", "Nguyễn Thị Phương",  "0912345628", "phuong.nguyen@example.com",2, doiId, "Phòng đôi",        nm.plusDays(17), nm.plusDays(20), 500000, 650000,  2);
        insertBooking("BK-S4-029", "Trần Đăng Khoa",     "0912345629", "khoa.tran@example.com",    2, stuId, "Studio Người Lớn", nm.plusDays(20), nm.plusDays(23), 700000, 900000,  2);
        insertBooking("BK-S4-030", "Lương Minh Quân",    "0912345630", "quan.luong@example.com",   1, donId, "Phòng đơn",        nm.plusDays(23), nm.plusDays(26), 300000, 400000,  1);

        return 30;
    }

    private void insertBooking(
            String code, String guestName, String phone, String email,
            int guestCount, Long roomTypeId, String roomTypeName,
            LocalDate checkIn, LocalDate checkOut,
            long weekdayPrice, long weekendPrice, int nights) {

        if (roomTypeId == null) return;
        long total = weekdayPrice * nights;
        OffsetDateTime now = OffsetDateTime.now();
        String status = checkOut.isBefore(LocalDate.now()) ? "DA_TRA_PHONG" : "DA_XAC_NHAN";

        jdbcTemplate.update("""
            INSERT INTO bookings (
                booking_code, guest_name, guest_phone, guest_email, guest_count,
                room_type_id, room_type_name_snapshot,
                check_in_date, check_out_date,
                weekday_price_snapshot, weekend_price_snapshot, weekend_days_snapshot,
                total_amount, status, source, created_at,
                extra_guest_count, extra_person_fee_snapshot, surcharge_amount
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,'FRIDAY,SATURDAY',?,?,'TRUC_TUYEN',?,0,0,0)
            """,
                code, guestName, phone, email, guestCount,
                roomTypeId, roomTypeName,
                checkIn, checkOut,
                weekdayPrice, weekendPrice,
                total, status, now);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Summary DTO
    // ─────────────────────────────────────────────────────────────────────────

    public record SampleDataSummary(
            int roomTypeCount,
            int roomCount,
            int priceOverrideCount,
            int bookingCount) {
    }
}
