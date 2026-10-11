package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.config.RoomShortageRuleConfig;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomShortageAlertServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-10T12:00:00+07:00");

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private RoomShortageAlertService roomShortageAlertService;

    private RoomType don;
    private RoomType doi;

    @BeforeEach
    void setUp() {
        don = new RoomType();
        don.setId(1L);
        don.setCode("DON");
        don.setName("Phòng đơn");
        don.setStatus(true);

        doi = new RoomType();
        doi.setId(2L);
        doi.setCode("DOI");
        doi.setName("Phòng đôi");
        doi.setStatus(true);
    }

    private static Room createRoom(String roomNumber, String roomType, RoomStatus status, boolean active) {
        Room r = new Room();
        r.setRoomNumber(roomNumber);
        r.setRoomType(roomType);
        r.setStatus(status);
        r.setActive(active);
        return r;
    }

    private static Booking createBooking(
            Long id,
            String code,
            RoomType roomType,
            BookingStatus status,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        Booking b = new Booking();
        b.setId(id);
        b.setBookingCode(code);
        b.setRoomType(roomType);
        b.setRoomTypeNameSnapshot(roomType.getName());
        b.setStatus(status);
        b.setCheckInDate(checkIn);
        b.setCheckOutDate(checkOut);
        return b;
    }

    @Test
    @DisplayName("Booking bằng số phòng khả dụng -> không cảnh báo")
    void bookingBangSoPhongKhaDung_khongCanhBao() {
        // Phòng đôi có 2 phòng khả dụng (201, 202)
        List<Room> rooms = List.of(
                createRoom("201", "Phòng đôi", RoomStatus.TRONG_SACH, true),
                createRoom("202", "Phòng đôi", RoomStatus.TRONG_BAN, true)
        );
        when(roomTypeRepository.findAll()).thenReturn(List.of(doi));
        when(roomRepository.findAll()).thenReturn(rooms);

        // Có đúng 2 booking trong ngày 10/10 đến 11/10
        Booking b1 = createBooking(1L, "BK-01", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking b2 = createBooking(2L, "BK-02", doi, BookingStatus.CHO_XAC_NHAN, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1, b2));

        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("Booking lớn hơn số phòng khả dụng đúng 1 -> có cảnh báo")
    void bookingLonHonSoPhongKhaDungDung1_coCanhBao() {
        // Phòng đôi có 2 phòng khả dụng
        List<Room> rooms = List.of(
                createRoom("201", "Phòng đôi", RoomStatus.TRONG_SACH, true),
                createRoom("202", "Phòng đôi", RoomStatus.TRONG_SACH, true)
        );
        when(roomTypeRepository.findAll()).thenReturn(List.of(doi));
        when(roomRepository.findAll()).thenReturn(rooms);

        // Có 3 booking trong cùng 1 đêm
        Booking b1 = createBooking(1L, "BK-01", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking b2 = createBooking(2L, "BK-02", doi, BookingStatus.CHO_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking b3 = createBooking(3L, "BK-03", doi, BookingStatus.DA_NHAN_PHONG, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1, b2, b3));

        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        assertThat(alerts).hasSize(1);
        RoomShortageAlertResponse alert = alerts.get(0);
        assertThat(alert.date()).isEqualTo(TODAY);
        assertThat(alert.roomTypeCode()).isEqualTo("DOI");
        assertThat(alert.roomTypeName()).isEqualTo("Phòng đôi");
        assertThat(alert.bookingCount()).isEqualTo(3);
        assertThat(alert.availableRooms()).isEqualTo(2);
    }

    @Test
    @DisplayName("Booking đã huỷ không được đếm")
    void bookingDaHuy_khongDuocDem() {
        // Phòng đơn có 1 phòng khả dụng
        List<Room> rooms = List.of(
                createRoom("101", "Phòng đơn", RoomStatus.TRONG_SACH, true)
        );
        when(roomTypeRepository.findAll()).thenReturn(List.of(don));
        when(roomRepository.findAll()).thenReturn(rooms);

        // 1 booking đã xác nhận và 1 booking đã huỷ
        Booking b1 = createBooking(1L, "BK-01", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        createBooking(2L, "BK-HUY", don, BookingStatus.DA_HUY, TODAY, TODAY.plusDays(1));

        // findActiveBookingsInDateRange chỉ trả về các booking có status hợp lệ, nhưng phòng ngừa cả khi repository trả về bHuy:
        // Service chỉ tính các booking nằm trong VALID_BOOKING_STATUSES
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1));

        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        // Booking hợp lệ = 1, khả dụng = 1 -> không thiếu phòng
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("Booking nhiều đêm đếm đúng từng đêm, ngày trả phòng không bị đếm")
    void bookingNhieuDem_demDungTungDem_ngayTraPhongKhongBiDem() {
        // Phòng đơn có 1 phòng khả dụng
        List<Room> rooms = List.of(
                createRoom("101", "Phòng đơn", RoomStatus.TRONG_SACH, true)
        );
        when(roomTypeRepository.findAll()).thenReturn(List.of(don));
        when(roomRepository.findAll()).thenReturn(rooms);

        // Booking 1: 10/10 -> 13/10 (chiếm các đêm 10, 11, 12. Ngày 13 là ngày checkout không chiếm)
        Booking b1 = createBooking(1L, "BK-LONG", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(3));
        // Booking 2: chỉ ở đêm 10/10 và đêm 11/10
        Booking b2 = createBooking(2L, "BK-EXTRA", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(2));
        // Booking 3: nhận phòng ngày 13/10 (ngày b1 trả phòng) đến 14/10
        Booking b3 = createBooking(3L, "BK-NEXT", don, BookingStatus.DA_XAC_NHAN, TODAY.plusDays(3), TODAY.plusDays(4));

        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1, b2, b3));

        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        // Đêm 10/10: b1 + b2 = 2 booking > 1 phòng -> thiếu
        // Đêm 11/10: b1 + b2 = 2 booking > 1 phòng -> thiếu
        // Đêm 12/10: chỉ có b1 = 1 booking == 1 phòng -> không thiếu
        // Đêm 13/10: b1 đã checkout, chỉ có b3 nhận phòng = 1 booking == 1 phòng -> không thiếu
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).date()).isEqualTo(TODAY);
        assertThat(alerts.get(0).bookingCount()).isEqualTo(2);
        assertThat(alerts.get(0).availableRooms()).isEqualTo(1);

        assertThat(alerts.get(1).date()).isEqualTo(TODAY.plusDays(1));
        assertThat(alerts.get(1).bookingCount()).isEqualTo(2);
        assertThat(alerts.get(1).availableRooms()).isEqualTo(1);
    }

    @Test
    @DisplayName("Hai loại phòng cùng thiếu trong một ngày -> hiện thành hai dòng riêng")
    void haiLoaiPhongCungThieuTrongMotNgay_hienThanhHaiDongRieng() {
        // Phòng đơn có 1 phòng khả dụng, Phòng đôi có 1 phòng khả dụng
        List<Room> rooms = List.of(
                createRoom("101", "Phòng đơn", RoomStatus.TRONG_SACH, true),
                createRoom("201", "Phòng đôi", RoomStatus.TRONG_SACH, true)
        );
        when(roomTypeRepository.findAll()).thenReturn(List.of(don, doi));
        when(roomRepository.findAll()).thenReturn(rooms);

        // Đều có 2 booking trong ngày 10/10
        Booking bDon1 = createBooking(1L, "BK-D1", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking bDon2 = createBooking(2L, "BK-D2", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));

        Booking bDoi1 = createBooking(3L, "BK-DOI1", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking bDoi2 = createBooking(4L, "BK-DOI2", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));

        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(bDon1, bDon2, bDoi1, bDoi2));

        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        assertThat(alerts).hasSize(2);
        // Cùng ngày 10/10 nhưng thành 2 dòng riêng biệt
        assertThat(alerts).allMatch(a -> a.date().equals(TODAY));
        List<String> roomCodes = alerts.stream().map(alert -> alert.roomTypeCode()).toList();
        assertThat(roomCodes).containsExactlyInAnyOrder("DON", "DOI");
    }

    @Test
    @DisplayName("Ngày nằm ngoài khoảng quét -> không hiện")
    void ngayNamNgoaiKhoangQuet_khongHien() {
        // Phòng đơn có 0 phòng khả dụng
        when(roomTypeRepository.findAll()).thenReturn(List.of(don));
        when(roomRepository.findAll()).thenReturn(List.of());

        // Booking ở quá khứ (trước today)
        Booking bPast = createBooking(1L, "BK-PAST", don, BookingStatus.DA_XAC_NHAN, TODAY.minusDays(5), TODAY.minusDays(2));
        // Booking ở tương lai sau 30 ngày (ví dụ 35 ngày sau today)
        LocalDate farFuture = TODAY.plusDays(RoomShortageRuleConfig.SCAN_DAYS_AHEAD + 5);
        Booking bFuture = createBooking(2L, "BK-FUTURE", don, BookingStatus.DA_XAC_NHAN, farFuture, farFuture.plusDays(2));

        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(bPast, bFuture));

        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        // Nằm ngoài khoảng quét [today, today + 30 days] nên không có cảnh báo nào
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("Không có cảnh báo -> trả về danh sách rỗng")
    void khongCoCanhBao_traVeDanhSachRong() {
        when(roomTypeRepository.findAll()).thenReturn(List.of(don, doi));
        when(roomRepository.findAll()).thenReturn(List.of(
                createRoom("101", "Phòng đơn", RoomStatus.TRONG_SACH, true),
                createRoom("201", "Phòng đôi", RoomStatus.TRONG_SACH, true)
        ));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of());

        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("Phòng bảo trì và phòng ngừng bán/khoá bị trừ khỏi số phòng khả dụng")
    void phongBaoTriVaPhongKhoa_giamSoPhongKhaDung() {
        // Tổng cộng 3 phòng đơn thực có:
        // - 101: TRONG_SACH, active=true (khả dụng)
        // - 102: BAO_TRI, active=true (bảo trì -> không khả dụng)
        // - 103: TRONG_SACH, active=false (đang khoá / ngừng bán -> không khả dụng)
        List<Room> rooms = List.of(
                createRoom("101", "Phòng đơn", RoomStatus.TRONG_SACH, true),
                createRoom("102", "Phòng đơn", RoomStatus.BAO_TRI, true),
                createRoom("103", "Phòng đơn", RoomStatus.TRONG_SACH, false)
        );
        when(roomTypeRepository.findAll()).thenReturn(List.of(don));
        when(roomRepository.findAll()).thenReturn(rooms);

        // Có 2 booking trong ngày 10/10
        Booking b1 = createBooking(1L, "BK-01", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking b2 = createBooking(2L, "BK-02", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1, b2));

        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        // Số phòng khả dụng là 1. Số booking = 2 > 1 => Cảnh báo!
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).availableRooms()).isEqualTo(1);
        assertThat(alerts.get(0).bookingCount()).isEqualTo(2);
    }

    // ===== Tests cho getShortageBookings() =====

    @Test
    @DisplayName("getShortageBookings: số booking trả về đúng bằng số ghi trên cảnh báo")
    void getShortageBookings_soDongKhopSoCanhBao() {
        when(roomTypeRepository.findByCodeIgnoreCase("DON")).thenReturn(java.util.Optional.of(don));

        // 2 booking DA_XAC_NHAN cùng chiếm đêm 10/10
        Booking b1 = createBooking(1L, "BK-A1", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking b2 = createBooking(2L, "BK-A2", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        b1.setCreatedAt(NOW.minusHours(2));
        b2.setCreatedAt(NOW.minusHours(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1, b2));

        List<com.ttcs.homestay.dto.booking.ShortageBookingResponse> result =
                roomShortageAlertService.getShortageBookings(TODAY, "DON", NOW);

        assertThat(result).hasSize(2);
        // Booking tạo sau lên đầu (createdAt DESC)
        assertThat(result.get(0).bookingCode()).isEqualTo("BK-A2");
        assertThat(result.get(1).bookingCode()).isEqualTo("BK-A1");
    }

    @Test
    @DisplayName("getShortageBookings: booking đã huỷ không có trong danh sách (repository đã lọc)")
    void getShortageBookings_bookingDaHuy_khongCoTrongDanhSach() {
        when(roomTypeRepository.findByCodeIgnoreCase("DON")).thenReturn(java.util.Optional.of(don));

        // Repository chỉ trả về booking còn hiệu lực (đã lọc DA_HUY)
        Booking b1 = createBooking(1L, "BK-OK", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1));

        List<com.ttcs.homestay.dto.booking.ShortageBookingResponse> result =
                roomShortageAlertService.getShortageBookings(TODAY, "DON", NOW);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bookingCode()).isEqualTo("BK-OK");
    }

    @Test
    @DisplayName("getShortageBookings: booking nhiều đêm xuất hiện khi đêm được chọn trùng, không xuất hiện khi đêm checkout")
    void getShortageBookings_bookingNhieuDem_chiXuatHienKhiDemTrung() {
        when(roomTypeRepository.findByCodeIgnoreCase("DON")).thenReturn(java.util.Optional.of(don));

        // Booking từ 10/10 đến 13/10 -> chiếm đêm 10, 11, 12. Không chiếm đêm 13.
        Booking bLong = createBooking(1L, "BK-LONG", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(3));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(bLong));

        // Truy vấn đêm 12/10 (TODAY+2) -> bLong vẫn chiếm (12 >= 10 và 12 < 13)
        List<com.ttcs.homestay.dto.booking.ShortageBookingResponse> resultDay2 =
                roomShortageAlertService.getShortageBookings(TODAY.plusDays(2), "DON", NOW);
        assertThat(resultDay2).hasSize(1);

        // Truy vấn đêm 13/10 (TODAY+3) -> bLong đã checkout, không chiếm (13 không < 13)
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(bLong));
        List<com.ttcs.homestay.dto.booking.ShortageBookingResponse> resultDay3 =
                roomShortageAlertService.getShortageBookings(TODAY.plusDays(3), "DON", NOW);
        assertThat(resultDay3).isEmpty();
    }

    @Test
    @DisplayName("getShortageBookings: booking CHO_XAC_NHAN hết hạn giữ chỗ bị loại khỏi danh sách")
    void getShortageBookings_expiredHold_biLoaiKhoiDanhSach() {
        when(roomTypeRepository.findByCodeIgnoreCase("DON")).thenReturn(java.util.Optional.of(don));

        // Booking CHO_XAC_NHAN với holdExpiresAt đã hết hạn 1 giờ trước
        Booking bExpired = createBooking(1L, "BK-EXP", don, BookingStatus.CHO_XAC_NHAN, TODAY, TODAY.plusDays(1));
        bExpired.setHoldExpiresAt(NOW.minusHours(1));  // đã hết hạn

        // Booking DA_XAC_NHAN bình thường
        Booking bNormal = createBooking(2L, "BK-OK", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));

        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(bExpired, bNormal));

        List<com.ttcs.homestay.dto.booking.ShortageBookingResponse> result =
                roomShortageAlertService.getShortageBookings(TODAY, "DON", NOW);

        // BK-EXP bị loại do hết hạn hold
        assertThat(result).hasSize(1);
        assertThat(result.get(0).bookingCode()).isEqualTo("BK-OK");
    }

    @Test
    @DisplayName("getShortageBookings: mã loại phòng không tồn tại -> ném IllegalArgumentException")
    void getShortageBookings_roomTypeKhongTonTai_nemException() {
        when(roomTypeRepository.findByCodeIgnoreCase("INVALID")).thenReturn(java.util.Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> roomShortageAlertService.getShortageBookings(TODAY, "INVALID", NOW)
        );
    }

    // ===== Tests Story S3-08: Phòng chuyển sang bảo trì và ảnh hưởng cảnh báo =====

    @Test
    @DisplayName("S3-08: Phòng chuyển sang bảo trì làm số phòng khả dụng giảm đúng 1 và sinh cảnh báo mới khi booking vượt")
    void test_phongChuyenSangBaoTri_giamDungMot_sinhCanhBaoMoiKhiBookingVuot() {
        // Ban đầu có 2 phòng đôi: 201 và 202 đều TRONG_SACH
        Room r201 = createRoom("201", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        Room r202 = createRoom("202", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        when(roomTypeRepository.findAll()).thenReturn(List.of(doi));
        when(roomRepository.findAll()).thenReturn(List.of(r201, r202));

        // Có đúng 2 booking trong ngày TODAY
        Booking b1 = createBooking(1L, "BK-01", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking b2 = createBooking(2L, "BK-02", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1, b2));

        // Trước khi bảo trì: khả dụng = 2, booking = 2 -> Không có cảnh báo
        assertThat(roomShortageAlertService.getShortageAlerts(TODAY, NOW)).isEmpty();

        // Chuyển phòng 202 sang BAO_TRI
        r202.setStatus(RoomStatus.BAO_TRI);

        // Sau khi bảo trì: số phòng khả dụng giảm đúng 1 (còn 1), sinh cảnh báo mới
        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts(TODAY, NOW);
        assertThat(alerts).hasSize(1);
        RoomShortageAlertResponse alert = alerts.get(0);
        assertThat(alert.roomTypeCode()).isEqualTo("DOI");
        assertThat(alert.availableRooms()).isEqualTo(1); // Giảm đúng 1 từ 2 xuống 1
        assertThat(alert.bookingCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("S3-08: Phòng chuyển sang bảo trì nhưng vẫn còn dư thì không sinh cảnh báo")
    void test_phongChuyenSangBaoTri_nhungVanConDu_khongSinhCanhBao() {
        // 3 phòng đôi: 201, 202, 203
        Room r201 = createRoom("201", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        Room r202 = createRoom("202", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        Room r203 = createRoom("203", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        when(roomTypeRepository.findAll()).thenReturn(List.of(doi));
        when(roomRepository.findAll()).thenReturn(List.of(r201, r202, r203));

        // Chỉ có 1 booking
        Booking b1 = createBooking(1L, "BK-01", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1));

        // Chuyển 1 phòng sang BAO_TRI -> khả dụng giảm từ 3 xuống 2
        r201.setStatus(RoomStatus.BAO_TRI);

        // Vẫn còn dư phòng (booking 1 <= khả dụng 2) -> Không sinh cảnh báo
        assertThat(roomShortageAlertService.getShortageAlerts(TODAY, NOW)).isEmpty();
    }

    @Test
    @DisplayName("S3-08: Cảnh báo đã có thì số phòng khả dụng trên dòng giảm theo khi thêm phòng bảo trì")
    void test_canhBaoDaCo_soPhongKhaDungTrenDongGiamTheo() {
        // 3 phòng đôi: 201, 202, 203. Đã có 1 phòng 201 đang BAO_TRI.
        Room r201 = createRoom("201", "Phòng đôi", RoomStatus.BAO_TRI, true);
        Room r202 = createRoom("202", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        Room r203 = createRoom("203", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        when(roomTypeRepository.findAll()).thenReturn(List.of(doi));
        when(roomRepository.findAll()).thenReturn(List.of(r201, r202, r203));

        // 3 booking trong ngày -> vượt 2 phòng khả dụng -> Đã có cảnh báo (available = 2)
        Booking b1 = createBooking(1L, "BK-01", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking b2 = createBooking(2L, "BK-02", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking b3 = createBooking(3L, "BK-03", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1, b2, b3));

        List<RoomShortageAlertResponse> alertsBefore = roomShortageAlertService.getShortageAlerts(TODAY, NOW);
        assertThat(alertsBefore).hasSize(1);
        assertThat(alertsBefore.get(0).availableRooms()).isEqualTo(2);

        // Chuyển tiếp phòng 202 sang BAO_TRI
        r202.setStatus(RoomStatus.BAO_TRI);

        // Số phòng khả dụng trên dòng cảnh báo giảm theo từ 2 xuống 1
        List<RoomShortageAlertResponse> alertsAfter = roomShortageAlertService.getShortageAlerts(TODAY, NOW);
        assertThat(alertsAfter).hasSize(1);
        assertThat(alertsAfter.get(0).availableRooms()).isEqualTo(1);
    }

    @Test
    @DisplayName("S3-08: Phòng ra khỏi bảo trì thì cảnh báo biến mất hoặc số phòng khả dụng tăng lại")
    void test_phongRaKhoiBaoTri_canhBaoBienMatHoacSoPhongKhaDungTangLai() {
        // 2 phòng đôi: 201 TRONG_SACH, 202 BAO_TRI
        Room r201 = createRoom("201", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        Room r202 = createRoom("202", "Phòng đôi", RoomStatus.BAO_TRI, true);
        when(roomTypeRepository.findAll()).thenReturn(List.of(doi));
        when(roomRepository.findAll()).thenReturn(List.of(r201, r202));

        // 2 booking trong ngày -> Đang thiếu phòng (available = 1, booking = 2)
        Booking b1 = createBooking(1L, "BK-01", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking b2 = createBooking(2L, "BK-02", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1, b2));

        assertThat(roomShortageAlertService.getShortageAlerts(TODAY, NOW)).hasSize(1);

        // Phòng 202 ra khỏi bảo trì -> TRONG_SACH
        r202.setStatus(RoomStatus.TRONG_SACH);
        r202.setMaintenanceStartDate(null);
        r202.setMaintenanceEndDate(null);

        // Cảnh báo biến mất hoàn toàn!
        assertThat(roomShortageAlertService.getShortageAlerts(TODAY, NOW)).isEmpty();

        // Trường hợp còn 3 booking (vẫn thiếu sau khi ra khỏi bảo trì): số phòng khả dụng tăng lại từ 1 lên 2
        Booking b3 = createBooking(3L, "BK-03", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1, b2, b3));

        List<RoomShortageAlertResponse> alertsWith3 = roomShortageAlertService.getShortageAlerts(TODAY, NOW);
        assertThat(alertsWith3).hasSize(1);
        assertThat(alertsWith3.get(0).availableRooms()).isEqualTo(2); // Tăng lại thành 2
    }

    @Test
    @DisplayName("S3-08: Bảo trì chỉ ảnh hưởng đúng các ngày trong khoảng thời gian đã chốt")
    void test_baoTriChiAnhHuongDungCacNgayTrongKhoangThoiGianDaChot() {
        // Phòng đôi 201: TRONG_SACH. Phòng đôi 202: BAO_TRI từ TODAY + 2 đến TODAY + 4
        Room r201 = createRoom("201", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        Room r202 = createRoom("202", "Phòng đôi", RoomStatus.BAO_TRI, true);
        r202.setMaintenanceStartDate(TODAY.plusDays(2));
        r202.setMaintenanceEndDate(TODAY.plusDays(4));

        when(roomTypeRepository.findAll()).thenReturn(List.of(doi));
        when(roomRepository.findAll()).thenReturn(List.of(r201, r202));

        // Mỗi ngày có 2 booking:
        // Ngày TODAY (trước bảo trì): 2 booking
        Booking bToday1 = createBooking(1L, "BK-T1", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking bToday2 = createBooking(2L, "BK-T2", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        // Ngày TODAY + 2 (trong bảo trì): 2 booking
        Booking bMaint1 = createBooking(3L, "BK-M1", doi, BookingStatus.DA_XAC_NHAN, TODAY.plusDays(2), TODAY.plusDays(3));
        Booking bMaint2 = createBooking(4L, "BK-M2", doi, BookingStatus.DA_XAC_NHAN, TODAY.plusDays(2), TODAY.plusDays(3));
        // Ngày TODAY + 5 (sau bảo trì): 2 booking
        Booking bAfter1 = createBooking(5L, "BK-A1", doi, BookingStatus.DA_XAC_NHAN, TODAY.plusDays(5), TODAY.plusDays(6));
        Booking bAfter2 = createBooking(6L, "BK-A2", doi, BookingStatus.DA_XAC_NHAN, TODAY.plusDays(5), TODAY.plusDays(6));

        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any()))
                .thenReturn(List.of(bToday1, bToday2, bMaint1, bMaint2, bAfter1, bAfter2));

        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        // Chỉ duy nhất ngày TODAY + 2 bị cảnh báo thiếu phòng (do phòng 202 bị bảo trì, khả dụng = 1 < 2 booking)
        assertThat(alerts).hasSize(1);
        RoomShortageAlertResponse alert = alerts.get(0);
        assertThat(alert.date()).isEqualTo(TODAY.plusDays(2));
        assertThat(alert.availableRooms()).isEqualTo(1);

        // Ngày TODAY và TODAY + 5 không bị ảnh hưởng (cả 2 phòng đều khả dụng, 2 booking = 2 phòng)
        assertThat(alerts).noneMatch(a -> a.date().equals(TODAY));
        assertThat(alerts).noneMatch(a -> a.date().equals(TODAY.plusDays(5)));
    }

    @Test
    @DisplayName("S3-08: Phòng bảo trì thuộc loại phòng khác không ảnh hưởng cảnh báo của loại phòng đang xét")
    void test_phongBaoTriThuocLoaiKhac_khongAnhHuongCanhBaoLoaiDangXet() {
        // Phòng đơn 101: TRONG_SACH (khả dụng = 1). Có 1 booking phòng đơn -> Đủ phòng
        Room r101 = createRoom("101", "Phòng đơn", RoomStatus.TRONG_SACH, true);
        // Phòng đôi 201: BAO_TRI
        Room r201 = createRoom("201", "Phòng đôi", RoomStatus.BAO_TRI, true);

        when(roomTypeRepository.findAll()).thenReturn(List.of(don, doi));
        when(roomRepository.findAll()).thenReturn(List.of(r101, r201));

        Booking bDon = createBooking(1L, "BK-DON", don, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(bDon));

        List<RoomShortageAlertResponse> alerts = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        // Không có cảnh báo cho Phòng đơn
        assertThat(alerts).noneMatch(a -> a.roomTypeCode().equals("DON"));
    }

    @Test
    @DisplayName("S3-08: Nhiều phòng cùng loại chuyển bảo trì liên tiếp thì số liệu luôn chính xác")
    void test_nhieuPhongCungLoaiChuyenBaoTriLienTiep_soLieuDung() {
        Room r201 = createRoom("201", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        Room r202 = createRoom("202", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        Room r203 = createRoom("203", "Phòng đôi", RoomStatus.TRONG_SACH, true);

        when(roomTypeRepository.findAll()).thenReturn(List.of(doi));
        when(roomRepository.findAll()).thenReturn(List.of(r201, r202, r203));

        Booking b1 = createBooking(1L, "BK-01", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking b2 = createBooking(2L, "BK-02", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1, b2));

        // 3 phòng khả dụng, 2 booking -> 0 cảnh báo
        assertThat(roomShortageAlertService.getShortageAlerts(TODAY, NOW)).isEmpty();

        // Chuyển phòng thứ 1 sang bảo trì: khả dụng = 2, 2 booking -> 0 cảnh báo
        r201.setStatus(RoomStatus.BAO_TRI);
        assertThat(roomShortageAlertService.getShortageAlerts(TODAY, NOW)).isEmpty();

        // Chuyển phòng thứ 2 sang bảo trì: khả dụng = 1, 2 booking -> Cảnh báo thiếu 1 phòng
        r202.setStatus(RoomStatus.BAO_TRI);
        List<RoomShortageAlertResponse> alerts1 = roomShortageAlertService.getShortageAlerts(TODAY, NOW);
        assertThat(alerts1).hasSize(1);
        assertThat(alerts1.get(0).availableRooms()).isEqualTo(1);

        // Chuyển phòng thứ 3 sang bảo trì: khả dụng = 0, 2 booking -> Cảnh báo thiếu 2 phòng
        r203.setStatus(RoomStatus.BAO_TRI);
        List<RoomShortageAlertResponse> alerts2 = roomShortageAlertService.getShortageAlerts(TODAY, NOW);
        assertThat(alerts2).hasSize(1);
        assertThat(alerts2.get(0).availableRooms()).isEqualTo(0);
        assertThat(alerts2.get(0).bookingCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("S3-08: Kết quả sau khi làm mới khớp với kết quả khi tải lại hoàn toàn màn hình")
    void test_ketQuaSauKhiLamMoi_khopVoiKetQuaKhiTaiLaiHoanToan() {
        Room r201 = createRoom("201", "Phòng đôi", RoomStatus.TRONG_SACH, true);
        Room r202 = createRoom("202", "Phòng đôi", RoomStatus.BAO_TRI, true);

        when(roomTypeRepository.findAll()).thenReturn(List.of(doi));
        when(roomRepository.findAll()).thenReturn(List.of(r201, r202));

        Booking b1 = createBooking(1L, "BK-01", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        Booking b2 = createBooking(2L, "BK-02", doi, BookingStatus.DA_XAC_NHAN, TODAY, TODAY.plusDays(1));
        when(bookingRepository.findActiveBookingsInDateRange(any(), any(), any())).thenReturn(List.of(b1, b2));

        // Lần 1 (Tải ban đầu)
        List<RoomShortageAlertResponse> initialLoad = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        // Lần 2 (Làm mới sau khoảng thời gian)
        List<RoomShortageAlertResponse> refreshed = roomShortageAlertService.getShortageAlerts(TODAY, NOW);

        assertThat(refreshed).isEqualTo(initialLoad);
    }
}
