package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.RoomUnavailableException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** S3-02 Lát 1: Phòng đôi có 2 phòng (201, 202). Booking 3 đêm 10/06 – 13/06/2027 được giữ một phòng cụ thể. */
@ExtendWith(MockitoExtension.class)
class RoomAssignmentTest {

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
        p201 = room(201L, "201");
        p202 = room(202L, "202");
        // Trả về 202 trước để chắc chắn phòng được chọn theo số phòng chứ không theo thứ tự trong danh sách.
        lenient().when(roomRepository.findByRoomTypeIgnoreCaseAndActiveTrue("Phòng đôi"))
                .thenReturn(List.of(p202, p201));
    }

    private static Room room(Long id, String number) {
        Room room = new Room();
        room.setId(id);
        room.setRoomNumber(number);
        room.setRoomType("Phòng đôi");
        room.setStatus(RoomStatus.TRONG_SACH);
        room.setActive(true);
        return room;
    }

    private static Booking holding(Long id, Room room, BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setRoom(room);
        booking.setStatus(status);
        booking.setCheckInDate(CHECK_IN);
        booking.setCheckOutDate(CHECK_OUT);
        return booking;
    }

    private void coBooking(Booking... bookings) {
        when(bookingRepository.findOverlappingOnRooms(anyCollection(), any(), any(), any()))
                .thenReturn(List.of(bookings));
    }

    @Test
    void conTrongCaHaiPhong_ganPhongSoNhoNhat() {
        coBooking();

        assertThat(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null)).isSameAs(p201);
    }

    @Test
    void phong201DaBiGiuCungDem_ganPhong202() {
        coBooking(holding(1L, p201, BookingStatus.DA_XAC_NHAN));

        assertThat(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null)).isSameAs(p202);
    }

    @Test
    void caHaiPhongDaBiGiu_baoHetPhong() {
        coBooking(holding(1L, p201, BookingStatus.DA_XAC_NHAN), holding(2L, p202, BookingStatus.DA_NHAN_PHONG));

        assertThatThrownBy(() -> roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null))
                .isInstanceOf(RoomUnavailableException.class)
                .hasMessageContaining("Phòng đôi đã hết phòng");
    }

    @Test
    void loaiPhongChuaCoPhongNao_baoHetPhong() {
        when(roomRepository.findByRoomTypeIgnoreCaseAndActiveTrue("Phòng đôi")).thenReturn(List.of());

        assertThatThrownBy(() -> roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null))
                .isInstanceOf(RoomUnavailableException.class);
    }

    @Test
    void bookingChoXacNhanQuaHanGiuCho_traPhongVaChuyenSangHetHan() {
        Booking quaHan = holding(1L, p201, BookingStatus.CHO_XAC_NHAN);
        quaHan.setHoldExpiresAt(OffsetDateTime.now().minusMinutes(1));
        coBooking(quaHan);

        assertThat(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null)).isSameAs(p201);
        assertThat(quaHan.getStatus()).isEqualTo(BookingStatus.DA_HET_HAN);
        verify(bookingRepository).saveAll(List.of(quaHan));
    }

    @Test
    void bookingChoXacNhanConHanGiuCho_vanGiuPhong() {
        Booking conHan = holding(1L, p201, BookingStatus.CHO_XAC_NHAN);
        conHan.setHoldExpiresAt(OffsetDateTime.now().plusHours(5));
        coBooking(conHan);

        assertThat(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null)).isSameAs(p202);
        assertThat(conHan.getStatus()).isEqualTo(BookingStatus.CHO_XAC_NHAN);
    }

    @Test
    void phong201BaoTriMotDemTrongKhoang_ganPhong202() {
        p201.setStatus(RoomStatus.BAO_TRI);
        p201.setMaintenanceStartDate(LocalDate.of(2027, 6, 12));
        p201.setMaintenanceEndDate(LocalDate.of(2027, 6, 20));
        coBooking();

        assertThat(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null)).isSameAs(p202);
    }

    @Test
    void phong201BaoTriBatDauDungNgayTraPhong_vanGanDuoc() {
        p201.setStatus(RoomStatus.BAO_TRI);
        p201.setMaintenanceStartDate(CHECK_OUT);
        p201.setMaintenanceEndDate(LocalDate.of(2027, 6, 20));
        coBooking();

        assertThat(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, null, null)).isSameAs(p201);
    }

    @Test
    void doiNgay_giuPhongHienTaiNeuConTrong() {
        coBooking();

        assertThat(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, 9L, p202)).isSameAs(p202);
    }

    @Test
    void doiNgay_bookingDangDoiKhongTuChanPhongCuaChinhNo() {
        coBooking(holding(9L, p202, BookingStatus.DA_XAC_NHAN), holding(1L, p201, BookingStatus.DA_XAC_NHAN));

        assertThat(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, 9L, p202)).isSameAs(p202);
    }

    @Test
    void doiNgay_phongHienTaiDaBiNguoiKhacGiu_chuyenSangPhongTrongKhac() {
        coBooking(holding(1L, p202, BookingStatus.DA_XAC_NHAN));

        assertThat(roomAvailabilityService.assignRoom(phongDoi, CHECK_IN, CHECK_OUT, 9L, p202)).isSameAs(p201);
    }

    @Test
    void nhanRaLoiTrungPhongTuCoSoDuLieu() {
        DataIntegrityViolationException trungPhong = new DataIntegrityViolationException("could not execute statement",
                new SQLException("conflicting key value violates exclusion constraint \"bookings_room_no_overlap\""));
        DataIntegrityViolationException loiKhac = new DataIntegrityViolationException("could not execute statement",
                new SQLException("duplicate key value violates unique constraint \"bookings_booking_code_unique\""));

        assertThat(RoomAvailabilityService.isRoomOverlapViolation(trungPhong)).isTrue();
        assertThat(RoomAvailabilityService.isRoomOverlapViolation(loiKhac)).isFalse();
    }

}
