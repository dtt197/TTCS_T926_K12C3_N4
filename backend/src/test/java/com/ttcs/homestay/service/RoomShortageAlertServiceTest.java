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
        List<String> roomCodes = alerts.stream().map(RoomShortageAlertResponse::roomTypeCode).toList();
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
}

