package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.pricing.NightlyPrice;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * - 30 booking phân bố trên 3 tháng (tháng trước, hiện tại, tháng sau), giá từng đêm theo bảng giá,
 *   có phòng, tiền cọc; booking đã đóng có quyết toán, booking đang ở có khách và giấy tờ
 * </p>
 */
@Service
public class SampleDataService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbcTemplate;
    private final PricingService pricingService;
    private final RoomTypeRepository roomTypeRepository;

    public SampleDataService(JdbcTemplate jdbcTemplate, PricingService pricingService,
            RoomTypeRepository roomTypeRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.pricingService = pricingService;
        this.roomTypeRepository = roomTypeRepository;
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

        // ── 6. Nạp tài khoản mẫu cho từng vai trò (S4-09) ────────────────────
        seedDemoAccounts();

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
        jdbcTemplate.execute("DELETE FROM email_logs");
        jdbcTemplate.execute("DELETE FROM appointment_action_logs");
        // S4-03: hoá đơn khoá chặt vào booking (ON DELETE RESTRICT), phải xoá trước và đếm lại số hoá đơn từ đầu
        jdbcTemplate.execute("DELETE FROM issued_invoices");
        jdbcTemplate.execute("DELETE FROM invoice_number_counters");
        jdbcTemplate.execute("DELETE FROM bookings");
        jdbcTemplate.execute("DELETE FROM price_overrides");
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM maintenance_request_history");
        jdbcTemplate.execute("DELETE FROM maintenance_requests");
        jdbcTemplate.execute("DELETE FROM room_status_history");
        jdbcTemplate.execute("DELETE FROM check_ins");
        jdbcTemplate.execute("DELETE FROM room_notes");
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
        // Phân bố: DON × 5, DOI × 7, GIA_DINH × 5, STUDIO_ADULT × 3 = 20.
        // Tất cả bắt đầu ở trạng thái trống sạch; trạng thái đang ở / trống bẩn được đặt theo booking ở seedBookings.
        jdbcTemplate.update("""
            INSERT INTO rooms (room_number, floor, room_type, status, active)
            VALUES
            -- Tầng 1: Phòng đơn
            ('101', 1, 'Phòng đơn',      'TRONG_SACH', TRUE),
            ('102', 1, 'Phòng đơn',      'TRONG_SACH', TRUE),
            -- Tầng 1: Phòng đôi
            ('103', 1, 'Phòng đôi',      'TRONG_SACH', TRUE),
            ('104', 1, 'Phòng đôi',      'TRONG_SACH', TRUE),
            ('105', 1, 'Phòng đôi',      'TRONG_SACH', TRUE),
            -- Tầng 2: Phòng đơn
            ('201', 2, 'Phòng đơn',      'TRONG_SACH', TRUE),
            ('202', 2, 'Phòng đơn',      'TRONG_SACH', TRUE),
            ('203', 2, 'Phòng đơn',      'TRONG_SACH', TRUE),
            -- Tầng 2: Phòng đôi
            ('204', 2, 'Phòng đôi',      'TRONG_SACH', TRUE),
            ('205', 2, 'Phòng đôi',      'TRONG_SACH', TRUE),
            ('206', 2, 'Phòng đôi',      'TRONG_SACH', TRUE),
            ('207', 2, 'Phòng đôi',      'TRONG_SACH', TRUE),
            -- Tầng 3: Phòng gia đình
            ('301', 3, 'Phòng gia đình', 'TRONG_SACH', TRUE),
            ('302', 3, 'Phòng gia đình', 'TRONG_SACH', TRUE),
            ('303', 3, 'Phòng gia đình', 'TRONG_SACH', TRUE),
            ('304', 3, 'Phòng gia đình', 'TRONG_SACH', TRUE),
            ('305', 3, 'Phòng gia đình', 'TRONG_SACH', TRUE),
            -- Tầng 4: Studio Người Lớn
            ('401', 4, 'Studio Người Lớn', 'TRONG_SACH', TRUE),
            ('402', 4, 'Studio Người Lớn', 'TRONG_SACH', TRUE),
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

    /**
     * Một booking mẫu. fixedStatus null thì suy ra theo ngày so với hôm nay:
     * đã qua ngày trả → đã trả phòng (có quyết toán), đang trong kỳ lưu trú → đang ở, chưa tới → đã xác nhận.
     */
    private record SampleBooking(String code, String guestName, String phone, String email, int guestCount,
            String roomTypeCode, String roomTypeName, LocalDate checkIn, LocalDate checkOut, String fixedStatus) {
    }

    private static final String ACTOR = "Lễ tân Demo";

    /**
     * DoD #8: mỗi bước của luồng đều có dữ liệu để thử ngay sau khi nạp:
     * booking đã đóng có phòng + quyết toán (xuất hoá đơn S4-03, doanh thu S4-07), booking đang ở có khách và
     * số giấy tờ (trả phòng S4-01, phụ thu S4-02), booking đã chốt phòng nhận hôm nay (nhận phòng S3-07),
     * phòng vừa trả cần dọn (buồng phòng S3-09). Giá từng đêm lấy từ PricingService (S2-01, S2-02).
     */
    private int seedBookings() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        List<SampleBooking> samples = new ArrayList<>(sampleBookings(today));
        samples.sort(Comparator.comparing(SampleBooking::checkIn).thenComparing(SampleBooking::code));

        List<Map<String, Object>> latestSettings = jdbcTemplate.queryForList(
                "SELECT id, weekend_days FROM operating_settings ORDER BY created_at DESC, id DESC");
        Long settingsId = latestSettings.isEmpty() ? null : ((Number) latestSettings.get(0).get("id")).longValue();
        Object weekendValue = latestSettings.isEmpty() ? null : latestSettings.get(0).get("weekend_days");
        String weekendDays = weekendValue == null || weekendValue.toString().isBlank()
                ? "FRIDAY,SATURDAY" : weekendValue.toString();
        Set<DayOfWeek> weekendSet = PricingService.parseWeekendDays(weekendDays);
        Map<String, List<Long>> roomsByType = new HashMap<>();
        jdbcTemplate.query("SELECT id, room_type FROM rooms ORDER BY room_number", (rs) -> {
            roomsByType.computeIfAbsent(rs.getString("room_type"), key -> new ArrayList<>()).add(rs.getLong("id"));
        });

        Map<Long, List<LocalDate[]>> roomStays = new HashMap<>();
        Set<Long> occupiedRooms = new HashSet<>();
        Set<Long> arrivalRooms = new HashSet<>();
        Map<Long, LocalDate> lastCheckout = new HashMap<>();
        int index = 0;
        for (SampleBooking sample : samples) {
            Long roomTypeId = getRoomTypeId(sample.roomTypeCode());
            if (roomTypeId == null) {
                continue;
            }
            String status = statusOf(sample, today);
            // Khách đang ở giữ phòng ít nhất tới hết hôm nay, để khách đến hôm nay không bị xếp trùng phòng chưa dọn.
            LocalDate holdUntil = "DA_NHAN_PHONG".equals(status) && !sample.checkOut().isAfter(today)
                    ? today.plusDays(1) : sample.checkOut();
            Long roomId = pickRoom(roomsByType.getOrDefault(sample.roomTypeName(), List.of()),
                    roomStays, sample.checkIn(), holdUntil);

            RoomType roomType = roomTypeRepository.findById(roomTypeId).orElseThrow();
            List<NightlyPrice> nightlyPrices = pricingService.priceNights(
                    roomType, sample.checkIn(), sample.checkOut(), weekendSet);
            long total = PricingService.total(nightlyPrices);
            long deposit = Math.min(total, Math.max(100_000L, total * 3 / 10 / 1_000 * 1_000));
            Long bookingId = insertBooking(sample, roomType, roomId, status, total, weekendDays);
            insertNightlySnapshots(bookingId, nightlyPrices);
            insertDeposit(bookingId, sample, deposit, index);

            if ("DA_NHAN_PHONG".equals(status) && roomId != null) {
                insertGuestAndCheckIn(bookingId, roomId, sample, index);
                occupiedRooms.add(roomId);
            } else if ("DA_XAC_NHAN".equals(status) && roomId != null && sample.checkIn().equals(today)) {
                arrivalRooms.add(roomId);
            } else if ("DA_TRA_PHONG".equals(status) && roomId != null) {
                insertSettlement(bookingId, roomId, sample, total, deposit, settingsId);
                lastCheckout.merge(roomId, sample.checkOut(), (left, right) -> left.isAfter(right) ? left : right);
            }
            index++;
        }
        updateRoomStatuses(occupiedRooms, arrivalRooms, lastCheckout);
        return samples.size();
    }

    private List<SampleBooking> sampleBookings(LocalDate today) {
        LocalDate pm = today.minusMonths(1).withDayOfMonth(1);
        LocalDate cm = today.withDayOfMonth(1);
        LocalDate nm = today.plusMonths(1).withDayOfMonth(1);
        return List.of(
                // ── Tháng trước: 10 booking ──
                new SampleBooking("BK-S4-001", "Nguyễn Văn An", "0912345601", "an.nguyen@example.com", 2, "DOI", "Phòng đôi", pm.plusDays(1), pm.plusDays(3), null),
                new SampleBooking("BK-S4-002", "Trần Thị Bình", "0912345602", "binh.tran@example.com", 1, "DON", "Phòng đơn", pm.plusDays(2), pm.plusDays(4), null),
                new SampleBooking("BK-S4-003", "Lê Hoàng Minh", "0912345603", "minh.le@example.com", 4, "GIA_DINH", "Phòng gia đình", pm.plusDays(5), pm.plusDays(8), null),
                new SampleBooking("BK-S4-004", "Phạm Thanh Hương", "0912345604", "huong.pham@example.com", 2, "STUDIO_ADULT", "Studio Người Lớn", pm.plusDays(6), pm.plusDays(9), null),
                new SampleBooking("BK-S4-005", "Vũ Đức Long", "0912345605", "long.vu@example.com", 2, "DOI", "Phòng đôi", pm.plusDays(10), pm.plusDays(12), null),
                new SampleBooking("BK-S4-006", "Đặng Thu Trang", "0912345606", "trang.dang@example.com", 1, "DON", "Phòng đơn", pm.plusDays(12), pm.plusDays(14), null),
                new SampleBooking("BK-S4-007", "Bùi Quang Huy", "0912345607", "huy.bui@example.com", 3, "GIA_DINH", "Phòng gia đình", pm.plusDays(14), pm.plusDays(17), null),
                new SampleBooking("BK-S4-008", "Hoàng Minh Châu", "0912345608", "chau.hoang@example.com", 2, "DOI", "Phòng đôi", pm.plusDays(18), pm.plusDays(20), null),
                new SampleBooking("BK-S4-009", "Ngô Thị Lan", "0912345609", "lan.ngo@example.com", 2, "STUDIO_ADULT", "Studio Người Lớn", pm.plusDays(20), pm.plusDays(22), null),
                new SampleBooking("BK-S4-010", "Trịnh Văn Khoa", "0912345610", "khoa.trinh@example.com", 1, "DON", "Phòng đơn", pm.plusDays(22), pm.plusDays(25), null),
                // ── Tháng hiện tại: 10 booking ──
                new SampleBooking("BK-S4-011", "Lý Thanh Thảo", "0912345611", "thao.ly@example.com", 2, "DOI", "Phòng đôi", cm.plusDays(1), cm.plusDays(4), null),
                new SampleBooking("BK-S4-012", "Dương Anh Tú", "0912345612", "tu.duong@example.com", 1, "DON", "Phòng đơn", cm.plusDays(3), cm.plusDays(5), null),
                new SampleBooking("BK-S4-013", "Đinh Thị Ngọc", "0912345613", "ngoc.dinh@example.com", 4, "GIA_DINH", "Phòng gia đình", cm.plusDays(5), cm.plusDays(9), null),
                new SampleBooking("BK-S4-014", "Lưu Bảo Nam", "0912345614", "nam.luu@example.com", 2, "STUDIO_ADULT", "Studio Người Lớn", cm.plusDays(7), cm.plusDays(10), null),
                new SampleBooking("BK-S4-015", "Hà Phương Thùy", "0912345615", "thuy.ha@example.com", 2, "DOI", "Phòng đôi", cm.plusDays(10), cm.plusDays(13), null),
                // 4 booking tính theo hôm nay để luôn có dữ liệu thử nhận phòng, trả phòng (kể cả trả muộn) và phụ thu
                new SampleBooking("BK-S4-016", "Tô Minh Đức", "0912345616", "duc.to@example.com", 1, "DON", "Phòng đơn", today.minusDays(2), today, null),
                new SampleBooking("BK-S4-017", "Cao Thị Hoa", "0912345617", "hoa.cao@example.com", 4, "GIA_DINH", "Phòng gia đình", today.minusDays(1), today.plusDays(2), null),
                new SampleBooking("BK-S4-018", "Phan Đình Toàn", "0912345618", "toan.phan@example.com", 2, "DOI", "Phòng đôi", today, today.plusDays(2), "DA_XAC_NHAN"),
                new SampleBooking("BK-S4-019", "Mai Thị Thu", "0912345619", "thu.mai@example.com", 2, "STUDIO_ADULT", "Studio Người Lớn", today, today.plusDays(1), "DA_XAC_NHAN"),
                new SampleBooking("BK-S4-020", "Đỗ Văn Tuyên", "0912345620", "tuyen.do@example.com", 1, "DON", "Phòng đơn", cm.plusDays(23), cm.plusDays(26), null),
                // ── Tháng tới: 10 booking ──
                new SampleBooking("BK-S4-021", "Trương Công Danh", "0912345621", "danh.truong@example.com", 2, "DOI", "Phòng đôi", nm.plusDays(1), nm.plusDays(4), null),
                new SampleBooking("BK-S4-022", "Kiều Thị Nga", "0912345622", "nga.kieu@example.com", 1, "DON", "Phòng đơn", nm.plusDays(3), nm.plusDays(6), null),
                new SampleBooking("BK-S4-023", "Châu Thanh Phong", "0912345623", "phong.chau@example.com", 4, "GIA_DINH", "Phòng gia đình", nm.plusDays(5), nm.plusDays(9), null),
                new SampleBooking("BK-S4-024", "Lê Thị Ánh", "0912345624", "anh.le@example.com", 2, "STUDIO_ADULT", "Studio Người Lớn", nm.plusDays(7), nm.plusDays(10), null),
                new SampleBooking("BK-S4-025", "Vương Hữu Phúc", "0912345625", "phuc.vuong@example.com", 2, "DOI", "Phòng đôi", nm.plusDays(10), nm.plusDays(13), null),
                new SampleBooking("BK-S4-026", "Tạ Thị Kim", "0912345626", "kim.ta@example.com", 1, "DON", "Phòng đơn", nm.plusDays(12), nm.plusDays(15), null),
                new SampleBooking("BK-S4-027", "Hồ Trung Hiếu", "0912345627", "hieu.ho@example.com", 3, "GIA_DINH", "Phòng gia đình", nm.plusDays(14), nm.plusDays(18), null),
                new SampleBooking("BK-S4-028", "Nguyễn Thị Phương", "0912345628", "phuong.nguyen@example.com", 2, "DOI", "Phòng đôi", nm.plusDays(17), nm.plusDays(20), null),
                new SampleBooking("BK-S4-029", "Trần Đăng Khoa", "0912345629", "khoa.tran@example.com", 2, "STUDIO_ADULT", "Studio Người Lớn", nm.plusDays(20), nm.plusDays(23), null),
                new SampleBooking("BK-S4-030", "Lương Minh Quân", "0912345630", "quan.luong@example.com", 1, "DON", "Phòng đơn", nm.plusDays(23), nm.plusDays(26), null));
    }

    private static String statusOf(SampleBooking sample, LocalDate today) {
        if (sample.fixedStatus() != null) {
            return sample.fixedStatus();
        }
        if (sample.checkOut().isBefore(today)) {
            return "DA_TRA_PHONG";
        }
        if (!sample.checkIn().isAfter(today)) {
            return "DA_NHAN_PHONG";
        }
        return "DA_XAC_NHAN";
    }

    /**
     * Phòng cùng loại không trùng đêm với booking đã xếp (khoảng nửa mở như S3-02); ưu tiên phòng ít booking nhất
     * để khách xếp đều các phòng, rồi tới số phòng nhỏ.
     */
    private static Long pickRoom(List<Long> candidates, Map<Long, List<LocalDate[]>> roomStays,
            LocalDate checkIn, LocalDate holdUntil) {
        Long chosen = null;
        int fewestStays = Integer.MAX_VALUE;
        for (Long roomId : candidates) {
            List<LocalDate[]> stays = roomStays.computeIfAbsent(roomId, key -> new ArrayList<>());
            boolean free = stays.stream().noneMatch(stay -> stay[0].isBefore(holdUntil) && stay[1].isAfter(checkIn));
            if (free && stays.size() < fewestStays) {
                chosen = roomId;
                fewestStays = stays.size();
            }
        }
        if (chosen != null) {
            roomStays.get(chosen).add(new LocalDate[] {checkIn, holdUntil});
        }
        return chosen;
    }

    private Long insertBooking(SampleBooking sample, RoomType roomType, Long roomId, String status,
            long total, String weekendDays) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
            INSERT INTO bookings (
                booking_code, guest_name, guest_phone, guest_email, guest_count,
                room_type_id, room_type_name_snapshot, room_id, room_confirmed_at,
                check_in_date, check_out_date,
                weekday_price_snapshot, weekend_price_snapshot, weekend_days_snapshot,
                total_amount, status, source, created_at,
                extra_guest_count, extra_person_fee_snapshot, surcharge_amount
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'TRUC_TUYEN',?,0,0,0)
            """,
                sample.code(), sample.guestName(), sample.phone(), sample.email(), sample.guestCount(),
                roomType.getId(), sample.roomTypeName(), roomId, roomId == null ? null : now,
                sample.checkIn(), sample.checkOut(),
                roomType.getWeekdayPrice(), roomType.getWeekendPrice(), weekendDays,
                total, status, now);
        return jdbcTemplate.queryForObject("SELECT id FROM bookings WHERE booking_code = ?", Long.class, sample.code());
    }

    private void insertNightlySnapshots(Long bookingId, List<NightlyPrice> nightlyPrices) {
        for (NightlyPrice night : nightlyPrices) {
            jdbcTemplate.update("""
                INSERT INTO booking_nightly_price_snapshots
                    (booking_id, night_date, price_type, price_label, room_price, surcharge_amount)
                VALUES (?, ?, ?, ?, ?, 0)
                """, bookingId, night.date(), night.priceType().name(), night.label(), night.price());
        }
    }

    private void insertDeposit(Long bookingId, SampleBooking sample, long deposit, int index) {
        boolean transfer = index % 2 == 1;
        jdbcTemplate.update("""
            INSERT INTO booking_deposits
                (booking_id, amount, payment_method, received_date, payment_reference, created_at, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, bookingId, BigDecimal.valueOf(deposit), transfer ? "BANK_TRANSFER" : "CASH",
                sample.checkIn().minusDays(3), transfer ? "CK-" + sample.code() : null, OffsetDateTime.now(), ACTOR);
    }

    /** Khách chính (kèm số giấy tờ mẫu 12 số) và lần nhận phòng lúc 14:30 ngày nhận phòng. */
    private void insertGuestAndCheckIn(Long bookingId, Long roomId, SampleBooking sample, int index) {
        jdbcTemplate.update("""
            INSERT INTO booking_guests (booking_id, guest_order, full_name, identity_number)
            VALUES (?, 0, ?, ?)
            """, bookingId, sample.guestName(), String.format("0012%08d", 4_567_800 + index));
        jdbcTemplate.update("INSERT INTO check_ins (room_id, guest_name, checked_in_at) VALUES (?, ?, ?)",
                roomId, sample.guestName(), atBusinessTime(sample.checkIn(), 14, 30));
    }

    /** Quyết toán lúc trả phòng 11:00 đúng giờ: thu tiền mặt phần còn lại sau cọc, không phụ thu. */
    private void insertSettlement(Long bookingId, Long roomId, SampleBooking sample,
            long total, long deposit, Long settingsId) {
        OffsetDateTime checkedOutAt = atBusinessTime(sample.checkOut(), 11, 0);
        long amount = total - deposit;
        jdbcTemplate.update("""
            INSERT INTO booking_settlements (
                booking_id, room_id, room_total, deposit_total, settlement_type, amount,
                payment_method, payment_reference, transaction_status, acted_by, settled_at, checked_out_at,
                late_checkout_fee, additional_surcharge_total, final_total, late_checkout_hours,
                late_checkout_fee_per_hour, checkout_time_snapshot, operating_settings_id, late_checkout_pricing_basis
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, 0, 0, ?, 0, NULL, ?, ?, 'NONE')
            """,
                bookingId, roomId, BigDecimal.valueOf(total), BigDecimal.valueOf(deposit),
                amount > 0 ? "COLLECT" : "ZERO", BigDecimal.valueOf(amount),
                amount > 0 ? "CASH" : null, amount > 0 ? "CONFIRMED" : "NOT_APPLICABLE", ACTOR,
                checkedOutAt, checkedOutAt, total, LocalTime.of(12, 0), settingsId);
    }

    /** Phòng có khách đang ở → đang ở; 2 phòng vừa trả gần nhất (không phải phòng đón khách hôm nay) → trống bẩn. */
    private void updateRoomStatuses(Set<Long> occupiedRooms, Set<Long> arrivalRooms, Map<Long, LocalDate> lastCheckout) {
        for (Long roomId : occupiedRooms) {
            jdbcTemplate.update("UPDATE rooms SET status = 'DANG_O' WHERE id = ?", roomId);
        }
        lastCheckout.entrySet().stream()
                .filter(entry -> !occupiedRooms.contains(entry.getKey()) && !arrivalRooms.contains(entry.getKey()))
                .sorted(Map.Entry.<Long, LocalDate>comparingByValue().reversed())
                .limit(2)
                .forEach(entry -> jdbcTemplate.update("UPDATE rooms SET status = 'TRONG_BAN' WHERE id = ?", entry.getKey()));
    }

    private static OffsetDateTime atBusinessTime(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tài khoản mẫu cho từng vai trò (S4-09)
    // ─────────────────────────────────────────────────────────────────────────

    private void seedDemoAccounts() {
        upsertUser("Chủ homestay Demo", "owner.demo@homestay.local", "OWNER");
        upsertUser("Lễ tân Demo", "letan.demo@homestay.local", "RECEPTIONIST");
        upsertUser("Buồng phòng Demo", "buongphong.demo@homestay.local", "HOUSEKEEPING");
        upsertUser("Demo Administrator", "admin.demo@homestay.local", "ADMIN");
    }

    private void upsertUser(String fullName, String email, String roleCode) {
        try {
            List<Long> roleIds = jdbcTemplate.queryForList("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
            Long roleId;
            if (roleIds.isEmpty()) {
                jdbcTemplate.update("INSERT INTO roles (code, name) VALUES (?, ?)", roleCode, roleCode);
                roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
            } else {
                roleId = roleIds.get(0);
            }
            if (roleId == null) return;

            // Mật khẩu mẫu chuẩn: "password"
            String passHash = "$2a$10$86R0Ma3Tt357LyDMkPMisO8KNPrFR5XpR974IaxOXAF8HPGjFrfWq";
            List<Integer> userExists = jdbcTemplate.queryForList(
                    "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)", Integer.class, email);
            if (userExists.isEmpty()) {
                jdbcTemplate.update("""
                    INSERT INTO users (
                        full_name, email, password_hash, role_id, is_active,
                        failed_login_count, token_version, must_change_password, created_at, updated_at)
                    VALUES (?, ?, ?, ?, TRUE, 0, 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, fullName, email, passHash, roleId);
            } else {
                jdbcTemplate.update("""
                    UPDATE users
                    SET password_hash = ?, role_id = ?, is_active = TRUE,
                        failed_login_count = 0, must_change_password = FALSE, updated_at = CURRENT_TIMESTAMP
                    WHERE LOWER(email) = LOWER(?)
                """, passHash, roleId, email);
            }
        } catch (Exception e) {
            // Không làm gián đoạn reload dữ liệu mẫu nếu có vấn đề về user
        }
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
