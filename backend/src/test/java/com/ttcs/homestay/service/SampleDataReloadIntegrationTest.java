package com.ttcs.homestay.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ttcs.homestay.dto.InvoiceData;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * S4-09 + DoD #8: nạp lại xong là có dữ liệu để thử từng bước của luồng
 * (nhận phòng, trả phòng, hoá đơn, doanh thu), giá khớp bảng giá và không trùng phòng.
 */
@SpringBootTest(properties = "app.demo-mode=true")
@Transactional
class SampleDataReloadIntegrationTest {

    private static final String ACTIVE = "('CHO_XAC_NHAN','DA_XAC_NHAN','DA_NHAN_PHONG')";

    @Autowired
    private SampleDataService sampleDataService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private InvoiceDataService invoiceDataService;
    @Autowired
    private CheckoutService checkoutService;

    private void givenHomestayContactInfo() {
        Integer settings = jdbc.queryForObject("SELECT COUNT(*) FROM operating_settings", Integer.class);
        if (settings == null || settings == 0) {
            jdbc.update("""
                INSERT INTO operating_settings (
                    homestay_name, address, phone, check_in_time, check_out_time, late_checkout_fee_per_hour,
                    extra_person_fee, created_by_name, weekend_days, created_at)
                VALUES ('HomeStay', '12 Hoa Sữa, Đà Lạt', '0263123456', '14:00', '12:00', 100000, 200000,
                        'Hệ thống', 'FRIDAY,SATURDAY', CURRENT_TIMESTAMP - INTERVAL '1' DAY)
                """);
        } else {
            jdbc.update("UPDATE operating_settings SET address = '12 Hoa Sữa, Đà Lạt', phone = '0263123456'");
        }
    }

    @Test
    void napLaiCoDuDuLieuDeThuMoiBuocCuaLuong() {
        givenHomestayContactInfo();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        assertEquals(30, sampleDataService.reload().bookingCount());

        // Giá lưu khớp tổng giá từng đêm (đối chiếu giá báo và giá lưu trên 30 booking mẫu)
        assertEquals(0, jdbc.queryForObject("""
            SELECT COUNT(*) FROM bookings b
            WHERE b.total_amount <> (SELECT COALESCE(SUM(n.room_price), 0)
                                     FROM booking_nightly_price_snapshots n WHERE n.booking_id = b.id)
            """, Integer.class));

        // Không có hai booking còn hiệu lực chiếm cùng phòng cùng đêm (S3-02)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bookings a JOIN bookings b"
                + " ON a.id < b.id AND a.room_id = b.room_id"
                + " WHERE a.status IN " + ACTIVE + " AND b.status IN " + ACTIVE
                + " AND a.check_in_date < b.check_out_date AND b.check_in_date < a.check_out_date", Integer.class));

        // Khách đang ở: phòng đang ở, có khách chính kèm số giấy tờ
        List<Long> inHouse = jdbc.queryForList("""
            SELECT b.id FROM bookings b JOIN rooms r ON r.id = b.room_id
            JOIN booking_guests g ON g.booking_id = b.id AND g.guest_order = 0 AND g.identity_number IS NOT NULL
            WHERE b.status = 'DA_NHAN_PHONG' AND r.status = 'DANG_O'
            """, Long.class);
        assertTrue(inHouse.size() >= 2, "Cần ít nhất 2 booking đang ở");

        // Trả phòng hôm nay xem được bảng tính tiền (S4-01)
        Long checkoutTodayRoom = jdbc.queryForObject(
                "SELECT room_id FROM bookings WHERE booking_code = 'BK-S4-016' AND check_out_date = ?",
                Long.class, today);
        assertDoesNotThrow(() -> checkoutService.preview(checkoutTodayRoom));

        // Khách đến hôm nay: đã xác nhận, đã chốt phòng, phòng trống sạch (S3-07)
        assertEquals(2, jdbc.queryForObject("""
            SELECT COUNT(*) FROM bookings b JOIN rooms r ON r.id = b.room_id
            WHERE b.status = 'DA_XAC_NHAN' AND b.check_in_date = ? AND b.room_confirmed_at IS NOT NULL
              AND r.status = 'TRONG_SACH'
            """, Integer.class, today));

        // Booking đã đóng xuất được hoá đơn (S4-03)
        Long closedId = jdbc.queryForObject("SELECT id FROM bookings WHERE booking_code = 'BK-S4-001'", Long.class);
        InvoiceData invoice = invoiceDataService.getInvoiceData(closedId);
        assertEquals("BK-S4-001", invoice.bookingCode());

        // Có phòng vừa trả cần dọn (S3-09)
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM rooms WHERE status = 'TRONG_BAN'", Integer.class) >= 1);
    }
}
