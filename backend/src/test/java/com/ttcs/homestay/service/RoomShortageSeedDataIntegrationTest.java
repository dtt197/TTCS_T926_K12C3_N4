package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcs.homestay.dto.booking.RoomShortageAlertResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task #s3-08: Kiểm thử tích hợp với dữ liệu mẫu và kiểm chứng trực tiếp Tiêu chí hoàn thành AC1 & AC2.
 */
@SpringBootTest
@Transactional
class RoomShortageSeedDataIntegrationTest {

    @Autowired
    private RoomShortageAlertService roomShortageAlertService;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private RoomType rtDon;
    private RoomType rtDoi;
    private RoomType rtGiaDinh;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        // Xoá sạch booking hiện có để nạp chính xác bộ dữ liệu mẫu 3 loại phòng của AC1
        bookingRepository.deleteAll();

        // Lớp test khác (không rollback) có thể để lại phòng đang hoạt động trùng tên loại phòng mẫu, ví dụ "Phòng đôi".
        // Tắt hết phòng sẵn có để số phòng khả dụng đúng như kịch bản; thay đổi này được rollback sau mỗi test.
        List<Room> existingRooms = roomRepository.findAll();
        existingRooms.forEach(room -> room.setActive(false));
        roomRepository.saveAll(existingRooms);
        rtDon = roomTypeRepository.findByCodeIgnoreCase("DON")
                .orElseGet(() -> {
                    RoomType rt = new RoomType();
                    rt.setCode("DON");
                    rt.setName("Phòng đơn");
                    rt.setStandardCapacity(1);
                    rt.setMaxCapacity(2);
                    rt.setNumberOfBeds(1);
                    rt.setStatus(true);
                    return roomTypeRepository.save(rt);
                });

        rtDoi = roomTypeRepository.findByCodeIgnoreCase("DOI")
                .orElseGet(() -> {
                    RoomType rt = new RoomType();
                    rt.setCode("DOI");
                    rt.setName("Phòng đôi");
                    rt.setStandardCapacity(2);
                    rt.setMaxCapacity(3);
                    rt.setNumberOfBeds(1);
                    rt.setStatus(true);
                    return roomTypeRepository.save(rt);
                });

        rtGiaDinh = roomTypeRepository.findByCodeIgnoreCase("GIA_DINH")
                .orElseGet(() -> {
                    RoomType rt = new RoomType();
                    rt.setCode("GIA_DINH");
                    rt.setName("Phòng gia đình");
                    rt.setStandardCapacity(4);
                    rt.setMaxCapacity(5);
                    rt.setNumberOfBeds(2);
                    rt.setStatus(true);
                    return roomTypeRepository.save(rt);
                });

        // Đảm bảo trạng thái số phòng thực có của 3 loại:
        // Phòng đơn: 1 phòng khả dụng (101)
        // Phòng đôi: 2 phòng khả dụng (102, 201)
        // Phòng gia đình: 1 phòng khả dụng (301)
        ensureRoom("101", rtDon.getName(), RoomStatus.TRONG_SACH, true);
        ensureRoom("102", rtDoi.getName(), RoomStatus.TRONG_SACH, true);
        ensureRoom("201", rtDoi.getName(), RoomStatus.TRONG_SACH, true);
        ensureRoom("301", rtGiaDinh.getName(), RoomStatus.TRONG_SACH, true);

        // Nạp dữ liệu mẫu seed:
        // 1. Loại THIẾU phòng: Phòng đơn (1 phòng khả dụng), 2 booking trùng đêm today + 2
        //    Booking dài ngày: today + 1 đến today + 4
        saveBooking("BK-S308-DON-01", "Hoàng Văn Thiếu", rtDon, today.plusDays(1), today.plusDays(4), BookingStatus.DA_XAC_NHAN);
        //    Booking ngắn ngày: today + 2 đến today + 3
        saveBooking("BK-S308-DON-02", "Lê Thị Trùng", rtDon, today.plusDays(2), today.plusDays(3), BookingStatus.CHO_XAC_NHAN);

        // 2. Loại VỪA ĐỦ: Phòng đôi (2 phòng khả dụng), đúng 2 booking
        saveBooking("BK-S308-DOI-01", "Phạm Quốc Đủ 1", rtDoi, today.plusDays(1), today.plusDays(3), BookingStatus.DA_XAC_NHAN);
        saveBooking("BK-S308-DOI-02", "Phạm Quốc Đủ 2", rtDoi, today.plusDays(1), today.plusDays(3), BookingStatus.DA_NHAN_PHONG);

        // 3. Loại CÒN DƯ: Phòng gia đình (1 phòng khả dụng), 0 booking hiệu lực và 1 booking ĐÃ HUỶ
        saveBooking("BK-S308-GD-CANCELLED", "Vũ Đình Huỷ", rtGiaDinh, today.plusDays(1), today.plusDays(3), BookingStatus.DA_HUY);
    }

    private void ensureRoom(String number, String roomTypeName, RoomStatus status, boolean active) {
        Room r = roomRepository.findAll().stream()
                .filter(room -> number.equals(room.getRoomNumber()))
                .findFirst()
                .orElseGet(() -> {
                    Room newRoom = new Room();
                    newRoom.setRoomNumber(number);
                    newRoom.setFloor(1);
                    return newRoom;
                });
        r.setRoomType(roomTypeName);
        r.setStatus(status);
        r.setActive(active);
        r.setMaintenanceStartDate(null);
        r.setMaintenanceEndDate(null);
        roomRepository.save(r);
    }

    private Booking saveBooking(
            String code,
            String guestName,
            RoomType roomType,
            LocalDate checkIn,
            LocalDate checkOut,
            BookingStatus status
    ) {
        Booking b = new Booking();
        b.setBookingCode(code);
        b.setGuestName(guestName);
        b.setRoomType(roomType);
        b.setRoomTypeNameSnapshot(roomType.getName());
        b.setCheckInDate(checkIn);
        b.setCheckOutDate(checkOut);
        b.setStatus(status);
        b.setWeekdayPriceSnapshot(300_000L);
        b.setWeekendPriceSnapshot(400_000L);
        b.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        b.setTotalAmount(900_000L);
        b.setCreatedAt(OffsetDateTime.now());
        b.setExtraGuestCount(0);
        b.setExtraPersonFeeSnapshot(0L);
        b.setSurchargeAmount(0L);
        return bookingRepository.save(b);
    }

    @Test
    @DisplayName("AC1: Với dữ liệu mẫu, chỉ cảnh báo loại phòng bị thiếu; loại vừa đủ và còn dư không xuất hiện")
    void testAC1_duLieuMau_chiHienCanhBaoPhongThieu() {
        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts();

        // 1. Chỉ loại phòng đơn (DON) bị thiếu ở đêm today + 2
        assertThat(alerts).isNotEmpty();
        assertThat(alerts).allMatch(alert -> alert.roomTypeCode().equals("DON"));

        // 2. Loại phòng vừa đủ (DOI) và còn dư (GIA_DINH) không hề xuất hiện
        assertThat(alerts).noneMatch(alert -> alert.roomTypeCode().equals("DOI"));
        assertThat(alerts).noneMatch(alert -> alert.roomTypeCode().equals("GIA_DINH"));

        // 3. Kiểm tra nội dung dòng cảnh báo thiếu phòng khớp chính xác với dữ liệu mẫu
        RoomShortageAlertResponse shortageOnNight2 = alerts.stream()
                .filter(a -> a.date().equals(today.plusDays(2)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Phải có cảnh báo thiếu phòng đơn vào ngày today + 2"));

        assertThat(shortageOnNight2.roomTypeName()).isEqualTo("Phòng đơn");
        assertThat(shortageOnNight2.bookingCount()).isEqualTo(2); // 2 booking trùng đêm
        assertThat(shortageOnNight2.availableRooms()).isEqualTo(1); // Chỉ có 1 phòng khả dụng
    }

    @Test
    @DisplayName("AC2: Xoá hết booking dư thì danh sách cảnh báo rỗng ('Không có cảnh báo')")
    void testAC2_xoaHetBookingDu_khongConCanhBao() {
        // Trước khi xoá: đang có cảnh báo thiếu phòng đơn
        List<RoomShortageAlertResponse> alertsBefore = roomShortageAlertService.getShortageAlerts();
        assertThat(alertsBefore).isNotEmpty();

        // Xoá booking dư thừa (BK-S308-DON-02) hoặc chuyển thành DA_HUY
        Booking bookingDu = bookingRepository.findByBookingCode("BK-S308-DON-02").orElseThrow();
        bookingRepository.delete(bookingDu);

        // Sau khi xoá: Phòng đơn chỉ còn 1 booking cho 1 phòng khả dụng -> Vừa đủ
        List<RoomShortageAlertResponse> alertsAfter = roomShortageAlertService.getShortageAlerts();
        assertThat(alertsAfter).isEmpty();
    }

    @Test
    @DisplayName("AC4: Demo - Với loại phòng không có cảnh báo, chuyển sang bảo trì hiện thêm cảnh báo; ra khỏi bảo trì thì biến mất; loại còn dư bảo trì không sinh cảnh báo")
    void testAC4_demoKichBanChuyenPhongBaoTri_vaRaKhoiBaoTri() {
        // Xoá booking dư của phòng đơn để đưa hệ thống về trạng thái sạch (hoặc chỉ xét Phòng đôi và Phòng gia đình)
        Booking bookingDuDon = bookingRepository.findByBookingCode("BK-S308-DON-02").orElseThrow();
        bookingRepository.delete(bookingDuDon);

        // 1. Ban đầu: Phòng đôi có 2 phòng khả dụng (102, 201), có đúng 2 booking -> KHÔNG có cảnh báo cho Phòng đôi
        List<RoomShortageAlertResponse> alertsInit = roomShortageAlertService.getShortageAlerts();
        assertThat(alertsInit).noneMatch(a -> a.roomTypeCode().equals("DOI"));

        // 2. Chuyển phòng đôi 102 sang BAO_TRI trong khoảng ngày bao gồm booking (today đến today + 5)
        Room r102 = roomRepository.findAll().stream()
                .filter(r -> "102".equals(r.getRoomNumber()))
                .findFirst()
                .orElseThrow();
        r102.setStatus(RoomStatus.BAO_TRI);
        r102.setMaintenanceStartDate(today);
        r102.setMaintenanceEndDate(today.plusDays(5));
        roomRepository.save(r102);

        // -> Màn hình vận hành hiện THÊM cảnh báo với số phòng khả dụng giảm đúng 1 (từ 2 xuống 1)
        List<RoomShortageAlertResponse> alertsAfterMaint = roomShortageAlertService.getShortageAlerts();
        List<RoomShortageAlertResponse> doiAlerts = alertsAfterMaint.stream()
                .filter(a -> a.roomTypeCode().equals("DOI"))
                .toList();
        assertThat(doiAlerts).isNotEmpty();
        assertThat(doiAlerts.get(0).availableRooms()).isEqualTo(1); // Giảm đúng 1
        assertThat(doiAlerts.get(0).bookingCount()).isEqualTo(2);

        // 3. Chuyển phòng 102 ra khỏi bảo trì (về TRONG_SACH)
        r102.setStatus(RoomStatus.TRONG_SACH);
        r102.setMaintenanceStartDate(null);
        r102.setMaintenanceEndDate(null);
        roomRepository.save(r102);

        // -> Cảnh báo của Phòng đôi BIẾN MẤT hoàn toàn
        List<RoomShortageAlertResponse> alertsAfterExitMaint = roomShortageAlertService.getShortageAlerts();
        assertThat(alertsAfterExitMaint).noneMatch(a -> a.roomTypeCode().equals("DOI"));

        // 4. Chuyển một phòng sang bảo trì ở loại CÒN DƯ (Phòng gia đình 301, 0 booking)
        Room r301 = roomRepository.findAll().stream()
                .filter(r -> "301".equals(r.getRoomNumber()))
                .findFirst()
                .orElseThrow();
        r301.setStatus(RoomStatus.BAO_TRI);
        r301.setMaintenanceStartDate(today);
        r301.setMaintenanceEndDate(today.plusDays(5));
        roomRepository.save(r301);

        // -> KHÔNG sinh cảnh báo cho Phòng gia đình (vì 0 booking <= 0 khả dụng)
        List<RoomShortageAlertResponse> alertsAfterGdMaint = roomShortageAlertService.getShortageAlerts();
        assertThat(alertsAfterGdMaint).noneMatch(a -> a.roomTypeCode().equals("GIA_DINH"));
    }
}
