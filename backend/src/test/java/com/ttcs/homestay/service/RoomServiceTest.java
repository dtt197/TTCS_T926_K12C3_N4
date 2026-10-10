package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ttcs.homestay.dto.IncidentReportRequest;
import com.ttcs.homestay.dto.CheckInRequest;
import com.ttcs.homestay.dto.CheckInResponse;
import com.ttcs.homestay.dto.MaintenanceRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.CheckIn;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomStatusHistory;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.RoomStatusConflictException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.BookingGuestRepository;
import com.ttcs.homestay.repository.CheckInRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;

/**
 * Kiểm thử nghiệp vụ S1-10: Ngăn chuyển thẳng phòng đang ở sang bảo trì.
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    RoomRepository roomRepository;

    @Mock
    CheckInRepository checkInRepository;

    @Mock
    RoomStatusHistoryRepository roomStatusHistoryRepository;

    @Mock
    BookingRepository bookingRepository;

    @Mock
    BookingGuestRepository bookingGuestRepository;

    @Mock
    OperatingSettingsRepository operatingSettingsRepository;

    @InjectMocks
    RoomService roomService;

    @Test
    @DisplayName("Chỉ yêu cầu xác nhận trước 14:00 theo giờ nhận phòng chuẩn")
    void requiresEarlyCheckInConfirmation_usesFourteenHourBoundary() {
        assertThat(RoomService.requiresEarlyCheckInConfirmation(LocalTime.of(13, 59), false)).isTrue();
        assertThat(RoomService.requiresEarlyCheckInConfirmation(LocalTime.of(13, 59), true)).isFalse();
        assertThat(RoomService.requiresEarlyCheckInConfirmation(LocalTime.of(14, 0), false)).isFalse();
        assertThat(RoomService.requiresEarlyCheckInConfirmation(LocalTime.of(14, 1), false)).isFalse();
    }

    private Room createOccupiedRoom(Long id, String roomNumber) {
        Room room = new Room();
        room.setId(id);
        room.setRoomNumber(roomNumber);
        room.setFloor(2);
        room.setRoomType("Phòng gia đình");
        room.setStatus(RoomStatus.DANG_O);
        room.setActive(true);
        return room;
    }

    private Booking createAssignedBooking(Long id, BookingStatus status, Room room) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setBookingCode("BK-" + id);
        booking.setGuestName("Khách nhận phòng");
        booking.setStatus(status);
        booking.setRoom(room);
        RoomType roomType = new RoomType();
        roomType.setMaxCapacity(4);
        booking.setRoomType(roomType);
        booking.setRoomConfirmedAt(OffsetDateTime.parse("2026-10-10T08:00:00+07:00"));
        return booking;
    }

    private CheckInRequest checkInRequest(Long bookingId) {
        return new CheckInRequest(bookingId, "Khách nhận phòng", "012345678901", List.of(), true);
    }

    @Test
    @DisplayName("Nhận phòng booking đã xác nhận, đổi cả trạng thái phòng và booking")
    void checkIn_confirmedBookingAndCleanAssignedRoom_updatesStatusesAndVietnameseTimestamp() {
        Long roomId = 101L;
        Long bookingId = 501L;
        Room room = createOccupiedRoom(roomId, "101");
        room.setStatus(RoomStatus.TRONG_SACH);
        Booking booking = createAssignedBooking(bookingId, BookingStatus.DA_XAC_NHAN, room);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(roomRepository.save(room)).thenReturn(room);
        when(bookingGuestRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckInResponse response = roomService.checkIn(
                roomId,
                new CheckInRequest(
                        bookingId, "Nguyễn Minh Anh", "012345678901", List.of("Trần Minh Khoa"), true),
                "Lễ tân");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_NHAN_PHONG);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.DANG_O);
        assertThat(response.bookingStatus()).isEqualTo(BookingStatus.DA_NHAN_PHONG);
        assertThat(response.roomStatus()).isEqualTo(RoomStatus.DANG_O);
        assertThat(response.bookingCode()).isEqualTo("BK-" + bookingId);
        assertThat(response.guestName()).isEqualTo("Nguyễn Minh Anh");
        assertThat(response.registeredGuests())
                .extracting(guest -> guest.fullName())
                .containsExactly("Nguyễn Minh Anh", "Trần Minh Khoa");
        assertThat(response.registeredGuests().get(0).primary()).isTrue();
        assertThat(response.checkedInAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
        verify(bookingRepository).save(booking);
        verify(bookingGuestRepository).saveAll(any());
        verify(roomRepository).save(room);
        verify(roomStatusHistoryRepository).save(any(RoomStatusHistory.class));
    }

    @Test
    @DisplayName("Từ chối nhận phòng khi booking chưa xác nhận")
    void checkIn_unconfirmedBooking_rejectsWithoutChangingState() {
        Long roomId = 102L;
        Long bookingId = 502L;
        Room room = createOccupiedRoom(roomId, "102");
        room.setStatus(RoomStatus.TRONG_SACH);
        Booking booking = createAssignedBooking(bookingId, BookingStatus.CHO_XAC_NHAN, room);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> roomService.checkIn(roomId, checkInRequest(bookingId), "Lễ tân"))
                .isInstanceOf(RoomStatusConflictException.class)
                .hasMessageContaining("Chỉ booking đã xác nhận");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHO_XAC_NHAN);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_SACH);
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("Từ chối nhận phòng nếu booking chưa được gán phòng")
    void checkIn_bookingWithoutAssignedRoom_rejects() {
        Long bookingId = 503L;
        Booking booking = createAssignedBooking(bookingId, BookingStatus.DA_XAC_NHAN, null);
        booking.setRoomConfirmedAt(null);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> roomService.checkIn(101L, checkInRequest(bookingId), "Lễ tân"))
                .isInstanceOf(RoomStatusConflictException.class)
                .hasMessageContaining("chưa được gán");

        verify(roomRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("Từ chối nhận phòng nếu phòng được gán chưa sạch")
    void checkIn_assignedRoomIsDirty_rejectsWithoutChangingBooking() {
        Long roomId = 104L;
        Long bookingId = 504L;
        Room room = createOccupiedRoom(roomId, "104");
        room.setStatus(RoomStatus.TRONG_BAN);
        Booking booking = createAssignedBooking(bookingId, BookingStatus.DA_XAC_NHAN, room);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> roomService.checkIn(roomId, checkInRequest(bookingId), "Lễ tân"))
                .isInstanceOf(RoomStatusConflictException.class)
                .hasMessageContaining("Chỉ phòng Trống sạch");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_BAN);
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("Từ chối nhận phòng nếu CCCD khách chính không đủ 12 chữ số")
    void checkIn_invalidPrimaryIdentityNumber_rejectsBeforeLoadingBooking() {
        assertThatThrownBy(() -> roomService.checkIn(
                101L, new CheckInRequest(501L, "Nguyễn Minh Anh", "123", List.of(), true), "Lễ tân"))
                .hasMessageContaining("CCCD")
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        verify(bookingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Từ chối nhận phòng nếu thiếu tên khách chính")
    void checkIn_blankPrimaryGuestName_rejectsBeforeLoadingBooking() {
        assertThatThrownBy(() -> roomService.checkIn(
                101L, new CheckInRequest(501L, "  ", "012345678901", List.of(), true), "Lễ tân"))
                .hasMessageContaining("khách chính")
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        verify(bookingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Từ chối nhận phòng nếu thiếu tên khách đi kèm")
    void checkIn_blankAccompanyingGuestName_rejectsBeforeLoadingBooking() {
        assertThatThrownBy(() -> roomService.checkIn(
                101L, new CheckInRequest(
                        501L, "Nguyễn Minh Anh", "012345678901", List.of("  "), true), "Lễ tân"))
                .hasMessageContaining("khách đi kèm")
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        verify(bookingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Từ chối nhận phòng nếu số khách vượt sức chứa loại phòng")
    void checkIn_guestCountExceedsRoomCapacity_rejectsWithoutSaving() {
        Long roomId = 105L;
        Long bookingId = 505L;
        Room room = createOccupiedRoom(roomId, "105");
        room.setStatus(RoomStatus.TRONG_SACH);
        Booking booking = createAssignedBooking(bookingId, BookingStatus.DA_XAC_NHAN, room);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> roomService.checkIn(
                roomId,
                new CheckInRequest(
                        bookingId, "Nguyễn Minh Anh", "012345678901",
                        List.of("Khách 2", "Khách 3", "Khách 4", "Khách 5"), true),
                "Lễ tân"))
                .hasMessageContaining("sức chứa tối đa")
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        verify(bookingGuestRepository, never()).saveAll(any());
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("Chặn đưa phòng đang ở vào bảo trì, giữ nguyên trạng thái và không ghi lịch sử")
    void putIntoMaintenance_whenRoomIsOccupied_shouldRejectAndNotRecordHistory() {
        Long roomId = 201L;
        Room room = createOccupiedRoom(roomId, "201");
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));

        MaintenanceRequest request = new MaintenanceRequest(
                "Sửa điều hòa",
                LocalDate.now(),
                LocalDate.now().plusDays(2)
        );

        assertThatThrownBy(() -> roomService.putIntoMaintenance(roomId, request, "Lễ tân"))
                .isInstanceOf(RoomStatusConflictException.class)
                .hasMessageContaining("Không thể đưa phòng 201 đang ở vào bảo trì. Hãy trả phòng trước.");

        // Xác nhận trạng thái giữ nguyên là Đang ở
        assertThat(room.getStatus()).isEqualTo(RoomStatus.DANG_O);

        // Xác nhận không ghi nhận lịch sử và không lưu phòng khi bị từ chối
        verify(roomStatusHistoryRepository, never()).save(any(RoomStatusHistory.class));
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("Chặn đổi trạng thái trực tiếp từ đang ở sang bảo trì, giữ nguyên trạng thái và không ghi lịch sử")
    void updateStatus_whenChangingOccupiedToMaintenance_shouldRejectAndNotRecordHistory() {
        Long roomId = 201L;
        Room room = createOccupiedRoom(roomId, "201");
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updateStatus(roomId, RoomStatus.BAO_TRI, "Lễ tân"))
                .isInstanceOf(RoomStatusConflictException.class)
                .hasMessageContaining("Vui lòng dùng biểu mẫu bảo trì");

        assertThat(room.getStatus()).isEqualTo(RoomStatus.DANG_O);
        verify(roomStatusHistoryRepository, never()).save(any(RoomStatusHistory.class));
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("Sau khi trả phòng, phòng được phép chuyển sang bảo trì và lưu đầy đủ thông tin")
    void maintenanceFlow_afterCheckOut_shouldSucceed() {
        Long roomId = 201L;
        Room room = createOccupiedRoom(roomId, "201");
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Thao tác trả phòng
        RoomResponse checkOutResponse = roomService.checkOut(roomId, "Lễ tân");
        assertThat(checkOutResponse.status()).isEqualTo(RoomStatus.TRONG_BAN);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_BAN);

        // Xác nhận lịch sử trả phòng được ghi nhận (DANG_O -> TRONG_BAN)
        ArgumentCaptor<RoomStatusHistory> historyCaptor = ArgumentCaptor.forClass(RoomStatusHistory.class);
        verify(roomStatusHistoryRepository).save(historyCaptor.capture());
        RoomStatusHistory checkOutHistory = historyCaptor.getValue();
        assertThat(checkOutHistory.getPreviousStatus()).isEqualTo(RoomStatus.DANG_O);
        assertThat(checkOutHistory.getNewStatus()).isEqualTo(RoomStatus.TRONG_BAN);

        // 2. Thao tác đưa sang bảo trì theo trạng thái mới (TRONG_BAN -> BAO_TRI)
        LocalDate startDate = LocalDate.of(2026, 9, 27);
        LocalDate endDate = LocalDate.of(2026, 9, 29);
        MaintenanceRequest maintenanceRequest = new MaintenanceRequest("Sơn lại tường", startDate, endDate);

        RoomResponse maintenanceResponse = roomService.putIntoMaintenance(roomId, maintenanceRequest, "Lễ tân");
        assertThat(maintenanceResponse.status()).isEqualTo(RoomStatus.BAO_TRI);
        assertThat(maintenanceResponse.maintenanceReason()).isEqualTo("Sơn lại tường");
        assertThat(maintenanceResponse.maintenanceStartDate()).isEqualTo(startDate);
        assertThat(maintenanceResponse.maintenanceEndDate()).isEqualTo(endDate);

        // Xác nhận lịch sử bảo trì được lưu
        verify(roomStatusHistoryRepository, org.mockito.Mockito.times(2)).save(historyCaptor.capture());
        RoomStatusHistory maintenanceHistory = historyCaptor.getValue();
        assertThat(maintenanceHistory.getPreviousStatus()).isEqualTo(RoomStatus.TRONG_BAN);
        assertThat(maintenanceHistory.getNewStatus()).isEqualTo(RoomStatus.BAO_TRI);
        assertThat(maintenanceHistory.getMaintenanceReason()).isEqualTo("Sơn lại tường");
    }

    @Test
        @DisplayName("S1-10 AC3: bảo trì thiếu ngày kết thúc dự kiến bị từ chối, không lưu và không ghi lịch sử")
    void maintenanceWithoutExpectedEndDate_shouldReject() {
        Long roomId = 202L;
        Room room = new Room();
        room.setId(roomId);
        room.setRoomNumber("202");
        room.setFloor(2);
        room.setRoomType("Phòng đơn");
        room.setStatus(RoomStatus.TRONG_SACH);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.putIntoMaintenance(
                roomId,
                new MaintenanceRequest("Chờ linh kiện", LocalDate.now(), null),
                "Lễ tân"))
                .isInstanceOf(RoomStatusConflictException.class)
                .hasMessageContaining("ngày kết thúc dự kiến");

        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_SACH);
        verify(roomRepository, never()).save(any(Room.class));
        verify(roomStatusHistoryRepository, never()).save(any(RoomStatusHistory.class));
    }

    @Test
    @DisplayName("Không thể trả phòng khi phòng không ở trạng thái Đang ở")
    void checkOut_whenRoomIsNotOccupied_shouldThrowConflict() {
        Long roomId = 101L;
        Room room = new Room();
        room.setId(roomId);
        room.setRoomNumber("101");
        room.setStatus(RoomStatus.TRONG_SACH);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.checkOut(roomId, "Lễ tân"))
                .isInstanceOf(RoomStatusConflictException.class)
                .hasMessageContaining("Không thể trả phòng 101 vì phòng không ở trạng thái Đang ở.");

        verify(roomStatusHistoryRepository, never()).save(any(RoomStatusHistory.class));
    }

    @Test
    @DisplayName("S3-09: getRooms gắn cờ khách nhận trong ngày và giờ nhận phòng dự kiến")
    void getRooms_identifiesGuestCheckingInTodayAndExpectedCheckInTime() {
        Room room1 = new Room();
        room1.setId(1L);
        room1.setRoomNumber("101");
        room1.setRoomType("Phòng đôi");
        room1.setStatus(RoomStatus.TRONG_BAN);
        room1.setActive(true);

        Room room2 = new Room();
        room2.setId(2L);
        room2.setRoomNumber("102");
        room2.setRoomType("Phòng đơn");
        room2.setStatus(RoomStatus.TRONG_BAN);
        room2.setActive(true);

        when(roomRepository.findAllByActiveTrueOrderByRoomNumberAsc()).thenReturn(List.of(room1, room2));

        Booking todayBooking = new Booking();
        todayBooking.setRoomTypeNameSnapshot("Phòng đôi");
        todayBooking.setCheckInDate(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        todayBooking.setStatus(BookingStatus.DA_XAC_NHAN);

        when(bookingRepository.findByCheckInDateAndStatusIn(any(LocalDate.class), any()))
                .thenReturn(List.of(todayBooking));

        List<RoomResponse> responses = roomService.getRooms();

        assertThat(responses).hasSize(2);
        RoomResponse resp1 = responses.get(0);
        assertThat(resp1.roomNumber()).isEqualTo("101");
        assertThat(resp1.hasGuestCheckInToday()).isTrue();
        assertThat(resp1.expectedCheckInTime()).isEqualTo("14:00");

        RoomResponse resp2 = responses.get(1);
        assertThat(resp2.roomNumber()).isEqualTo("102");
        assertThat(resp2.hasGuestCheckInToday()).isFalse();
        assertThat(resp2.expectedCheckInTime()).isNull();
    }

    @Test
    @DisplayName("S3-09: Chuyển phòng từ trống bẩn sang trống sạch, ghi nhận người thao tác và thời điểm")
    void markRoomClean_fromDirtyToClean_updatesStatusAndRecordsHistoryWithOperatorAndTimestamp() {
        Long roomId = 101L;
        Room room = new Room();
        room.setId(roomId);
        room.setRoomNumber("101");
        room.setFloor(1);
        room.setRoomType("Phòng đôi");
        room.setStatus(RoomStatus.TRONG_BAN);
        room.setActive(true);

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomResponse response = roomService.updateStatus(roomId, RoomStatus.TRONG_SACH, "Buồng phòng Demo");

        assertThat(response.status()).isEqualTo(RoomStatus.TRONG_SACH);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_SACH);

        ArgumentCaptor<RoomStatusHistory> historyCaptor = ArgumentCaptor.forClass(RoomStatusHistory.class);
        verify(roomStatusHistoryRepository).save(historyCaptor.capture());
        RoomStatusHistory history = historyCaptor.getValue();
        assertThat(history.getPreviousStatus()).isEqualTo(RoomStatus.TRONG_BAN);
        assertThat(history.getNewStatus()).isEqualTo(RoomStatus.TRONG_SACH);
        assertThat(history.getChangedBy()).isEqualTo("Buồng phòng Demo");
        assertThat(history.getChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("S3-09: Thao tác đổi trạng thái lỗi không làm đổi trạng thái phòng và không ghi nhận lịch sử")
    void updateStatus_whenErrorOccurs_doesNotChangeRoomStatusAndDoesNotRecordHistory() {
        Long roomId = 102L;
        Room room = new Room();
        room.setId(roomId);
        room.setRoomNumber("102");
        room.setStatus(RoomStatus.TRONG_BAN);

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));

        // Cố gắng chuyển sang BAO_TRI mà không thông qua form bảo trì hợp lệ
        assertThatThrownBy(() -> roomService.updateStatus(roomId, RoomStatus.BAO_TRI, "Buồng phòng Demo"))
                .isInstanceOf(RoomStatusConflictException.class);

        // Trạng thái giữ nguyên
        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_BAN);
        verify(roomRepository, never()).save(any(Room.class));
        verify(roomStatusHistoryRepository, never()).save(any(RoomStatusHistory.class));
    }

    @Test
    @DisplayName("S3-09: Báo sự cố phòng trống bẩn chuyển sang bảo trì kèm ghi chú và lưu lịch sử")
    void reportIncident_onDirtyRoom_switchesToMaintenanceWithNoteAndSavesHistory() {
        Long roomId = 103L;
        Room room = new Room();
        room.setId(roomId);
        room.setRoomNumber("103");
        room.setStatus(RoomStatus.TRONG_BAN);
        room.setActive(true);

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        IncidentReportRequest req = new IncidentReportRequest("Hỏng vòi sen nhà tắm và chập công tắc");
        RoomResponse response = roomService.reportIncident(roomId, req, "Nhân viên buồng phòng");

        assertThat(response.status()).isEqualTo(RoomStatus.BAO_TRI);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.BAO_TRI);
        assertThat(room.getMaintenanceReason()).isEqualTo("Hỏng vòi sen nhà tắm và chập công tắc");
        assertThat(room.getMaintenanceStartDate()).isNotNull();
        assertThat(room.getMaintenanceEndDate()).isNull();

        ArgumentCaptor<RoomStatusHistory> captor = ArgumentCaptor.forClass(RoomStatusHistory.class);
        verify(roomStatusHistoryRepository).save(captor.capture());
        RoomStatusHistory savedHist = captor.getValue();
        assertThat(savedHist.getPreviousStatus()).isEqualTo(RoomStatus.TRONG_BAN);
        assertThat(savedHist.getNewStatus()).isEqualTo(RoomStatus.BAO_TRI);
        assertThat(savedHist.getChangedBy()).isEqualTo("Nhân viên buồng phòng");
        assertThat(savedHist.getMaintenanceReason()).isEqualTo("Hỏng vòi sen nhà tắm và chập công tắc");
    }

    @Test
    @DisplayName("S3-09: Không thể báo sự cố cho phòng không phải là trống bẩn")
    void reportIncident_onNonDirtyRoom_throwsConflictException() {
        Long roomId = 104L;
        Room room = new Room();
        room.setId(roomId);
        room.setRoomNumber("104");
        room.setStatus(RoomStatus.TRONG_SACH);
        room.setActive(true);

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));

        IncidentReportRequest req = new IncidentReportRequest("Hỏng đèn trần");
        assertThatThrownBy(() -> roomService.reportIncident(roomId, req, "Buồng phòng"))
                .isInstanceOf(RoomStatusConflictException.class)
                .hasMessageContaining("Chỉ có thể báo sự cố cho phòng đang ở trạng thái Trống bẩn");

        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_SACH);
        verify(roomRepository, never()).save(any(Room.class));
    }
}
