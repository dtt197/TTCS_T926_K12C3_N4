package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ttcs.homestay.dto.MaintenanceRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomStatusHistory;
import com.ttcs.homestay.exception.RoomStatusConflictException;
import com.ttcs.homestay.repository.BookingRepository;
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
    OperatingSettingsRepository operatingSettingsRepository;

    @InjectMocks
    RoomService roomService;

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
}
