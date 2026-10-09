package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** S2-07 Lát 1: Phòng đôi có 2 phòng (201, 202). Khách hỏi 3 đêm 10/06 – 13/06/2027. */
@ExtendWith(MockitoExtension.class)
class RoomAvailabilityServiceTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2027, 6, 10);
    private static final LocalDate CHECK_OUT = LocalDate.of(2027, 6, 13);

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private RoomAvailabilityService roomAvailabilityService;

    private RoomType phongDoi;
    private Room p201;
    private Room p202;

    @BeforeEach
    void setUp() {
        phongDoi = new RoomType();
        phongDoi.setId(1L);
        phongDoi.setName("Phòng đôi");
        p201 = room("201");
        p202 = room("202");
        when(roomRepository.findByRoomTypeIgnoreCaseAndActiveTrue("Phòng đôi")).thenReturn(List.of(p201, p202));
    }
    
    private static Room room(String number) {
        Room room = new Room();
        room.setId("201".equals(number) ? 201L : 202L);
        room.setRoomNumber(number);
        room.setStatus(RoomStatus.TRONG_SACH);
        return room;
    }

    private static Booking booking(BookingStatus status, LocalDate checkIn, LocalDate checkOut) {
        Booking booking = new Booking();
        booking.setStatus(status);
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkOut);
        return booking;
    }

    private void coBooking(Booking... bookings) {
        when(bookingRepository.findOverlapping(anyLong(), any(), any(), any())).thenReturn(List.of(bookings));
    }

    @Test
    void khongCoBooking_con2Phong() {
        coBooking();

        assertThat(roomAvailabilityService.availableRooms(phongDoi, CHECK_IN, CHECK_OUT)).isEqualTo(2);
    }

    @Test
    void motBookingTrungMotDem_tinhTheoDemItPhongNhat() {
        coBooking(booking(BookingStatus.DA_XAC_NHAN, LocalDate.of(2027, 6, 12), LocalDate.of(2027, 6, 14)));

        assertThat(roomAvailabilityService.availableRooms(phongDoi, CHECK_IN, CHECK_OUT)).isEqualTo(1);
    }

    @Test
    void haiBookingChongDemTraCuu_hetPhongTrong() {
        coBooking(
                booking(BookingStatus.DA_XAC_NHAN, LocalDate.of(2027, 6, 12), LocalDate.of(2027, 6, 14)),
                booking(BookingStatus.DA_NHAN_PHONG, LocalDate.of(2027, 6, 12), LocalDate.of(2027, 6, 13))
        );

        assertThat(roomAvailabilityService.availableRooms(phongDoi, CHECK_IN, CHECK_OUT)).isZero();
    }

    @Test
    void bookingTraPhongDungNgayNhan_khongTinhTrung() {
        // Trả phòng 10/06, khách mới nhận phòng 10/06: không chiếm đêm nào của khách mới.
        coBooking(booking(BookingStatus.DA_NHAN_PHONG, LocalDate.of(2027, 6, 8), CHECK_IN));

        assertThat(roomAvailabilityService.availableRooms(phongDoi, CHECK_IN, CHECK_OUT)).isEqualTo(2);
    }

    @Test
    void phongBaoTriMotDem_giamPhongTrong() {
        p202.setStatus(RoomStatus.BAO_TRI);
        p202.setMaintenanceStartDate(LocalDate.of(2027, 6, 11));
        p202.setMaintenanceEndDate(LocalDate.of(2027, 6, 11));
        coBooking(booking(BookingStatus.CHO_XAC_NHAN, CHECK_IN, CHECK_OUT));

        assertThat(roomAvailabilityService.availableRooms(phongDoi, CHECK_IN, CHECK_OUT)).isZero();
    }

    @Test
    void phongBaoTriKhongCoNgayKetThuc_banTuNgayBatDauVeSau() {
        p202.setStatus(RoomStatus.BAO_TRI);
        p202.setMaintenanceStartDate(LocalDate.of(2027, 6, 11));
        p202.setMaintenanceEndDate(null);
        coBooking();

        assertThat(roomAvailabilityService.availableRooms(phongDoi, CHECK_IN, CHECK_OUT)).isEqualTo(1);
    }

    @Test
    void cacKhoangBaoTriTruocTrongVaSauKyTraCuuDuocTinhDung() {
        p202.setStatus(RoomStatus.BAO_TRI);
        coBooking();

        assertBaoTri(2027, 6, 1, 2027, 6, 9, 2);
        assertBaoTri(2027, 6, 10, 2027, 6, 10, 1);
        assertBaoTri(2027, 6, 11, 2027, 6, 11, 1);
        assertBaoTri(2027, 6, 12, 2027, 6, 13, 1);
        assertBaoTri(2027, 6, 14, 2027, 6, 15, 2);
        assertBaoTri(2027, 6, 9, 2027, 6, 14, 1);
    }

    @Test
    void phongBaoTriBatDauSauKhoangTraCuu_khongGiamPhongTrong() {
        p202.setStatus(RoomStatus.BAO_TRI);
        p202.setMaintenanceStartDate(CHECK_OUT.plusDays(1));
        p202.setMaintenanceEndDate(null);
        coBooking();

        assertThat(roomAvailabilityService.availableRooms(phongDoi, CHECK_IN, CHECK_OUT)).isEqualTo(2);
    }

    private void assertBaoTri(
            int startYear,
            int startMonth,
            int startDay,
            int endYear,
            int endMonth,
            int endDay,
            int expectedAvailable
    ) {
        p202.setMaintenanceStartDate(LocalDate.of(startYear, startMonth, startDay));
        p202.setMaintenanceEndDate(LocalDate.of(endYear, endMonth, endDay));
        assertThat(roomAvailabilityService.availableRooms(phongDoi, CHECK_IN, CHECK_OUT))
                .isEqualTo(expectedAvailable);
    }

    @Test
    void bookingChoXacNhanQuaHanGiuCho_khongConChiemPhong() {
        Booking quaHan = booking(BookingStatus.CHO_XAC_NHAN, CHECK_IN, CHECK_OUT);
        quaHan.setHoldExpiresAt(OffsetDateTime.now().minusHours(1));
        Booking conHan = booking(BookingStatus.CHO_XAC_NHAN, CHECK_IN, CHECK_OUT);
        conHan.setHoldExpiresAt(OffsetDateTime.now().plusHours(5));
        coBooking(quaHan, conHan);

        assertThat(roomAvailabilityService.availableRooms(phongDoi, CHECK_IN, CHECK_OUT)).isEqualTo(1);
    }

    @Test
    void availableRooms_boPhongTrungLichHoacBaoTriMotDem() {
        p202.setMaintenanceStartDate(LocalDate.of(2027, 6, 11));
        p202.setMaintenanceEndDate(LocalDate.of(2027, 6, 11));
        Booking occupied = booking(BookingStatus.DA_XAC_NHAN, CHECK_IN, CHECK_OUT);
        occupied.setRoom(p201);
        when(bookingRepository.findOverlappingOnRooms(any(), any(), any(), any())).thenReturn(List.of(occupied));

        assertThat(roomAvailabilityService.listAvailableRooms(phongDoi, CHECK_IN, CHECK_OUT, 77L)).isEmpty();
    }

    @Test
    void availableRooms_traPhongTrongCaKy() {
        when(bookingRepository.findOverlappingOnRooms(any(), any(), any(), any())).thenReturn(List.of());
        assertThat(roomAvailabilityService.listAvailableRooms(phongDoi, CHECK_IN, CHECK_OUT, 77L))
                .extracting(Room::getRoomNumber).containsExactly("201", "202");
    }

    @Test
    void availableRooms_loaiBookingHienTaiNhungVanGiuPhongHienTaiNeuHopLe() {
        p201.setId(201L);
        p202.setId(202L);
        Booking currentBooking = booking(BookingStatus.DA_XAC_NHAN, CHECK_IN, CHECK_OUT);
        currentBooking.setId(77L);
        currentBooking.setRoom(p201);
        when(bookingRepository.findOverlappingOnRooms(any(), any(), any(), any()))
                .thenReturn(List.of(currentBooking));

        assertThat(roomAvailabilityService.listAvailableRooms(phongDoi, CHECK_IN, CHECK_OUT, 77L))
                .extracting(Room::getRoomNumber).containsExactly("201", "202");
    }

    @Test
    void availableRooms_danhSachRongKhiTatCaPhongBiBookingKhacChiếm() {
        p201.setId(201L);
        p202.setId(202L);
        Booking otherBooking201 = booking(BookingStatus.DA_XAC_NHAN, CHECK_IN, CHECK_OUT);
        otherBooking201.setId(78L);
        otherBooking201.setRoom(p201);
        Booking otherBooking202 = booking(BookingStatus.DA_NHAN_PHONG, CHECK_IN, CHECK_OUT);
        otherBooking202.setId(79L);
        otherBooking202.setRoom(p202);
        when(bookingRepository.findOverlappingOnRooms(any(), any(), any(), any()))
                .thenReturn(List.of(otherBooking201, otherBooking202));

        assertThat(roomAvailabilityService.listAvailableRooms(phongDoi, CHECK_IN, CHECK_OUT, 77L)).isEmpty();
    }
}
