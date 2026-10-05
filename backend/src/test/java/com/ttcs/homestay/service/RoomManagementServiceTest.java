package com.ttcs.homestay.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import com.ttcs.homestay.dto.CreatePhysicalRoomRequest;
import com.ttcs.homestay.dto.UpdatePhysicalRoomRequest;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomNote;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.repository.RoomManagementRepository;
import com.ttcs.homestay.repository.RoomNoteRepository;

@ExtendWith(MockitoExtension.class)
class RoomManagementServiceTest {

    @Mock
    private RoomManagementRepository roomRepository;

    @Mock
    private RoomNoteRepository roomNoteRepository;

    @Mock
    private FutureBookingImpactChecker bookingImpactChecker;

    @InjectMocks
    private RoomManagementService roomManagementService;

    @Test
    void updateConcurrentDuplicateRejectedByDatabase_returnsClearRoomNumberError() {
        Room room = new Room();
        room.setId(501L);
        room.setRoomNumber("501");
        room.setFloor(5);
        room.setRoomType("Phòng đôi");
        room.setStatus(RoomStatus.TRONG_SACH);
        room.setActive(true);

        when(roomRepository.findById(501L)).thenReturn(Optional.of(room));
        when(roomRepository.existsByRoomNumberIgnoreCaseAndIdNot("502", 501L)).thenReturn(false);
        when(bookingImpactChecker.check(eq(501L), anyString(), anyString(), anyString(), anyString(),
                eq(5), eq(5), any()))
                .thenReturn(new FutureBookingImpactChecker.FutureBookingImpact(true, 0, ""));
        when(roomRepository.saveAndFlush(any(Room.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate room_number",
                        new ConstraintViolationException("duplicate room_number",
                                new SQLException("duplicate key", "23505"), "rooms_room_number_key")));

        assertThatThrownBy(() -> roomManagementService.update(501L,
                new UpdatePhysicalRoomRequest("502", 5, "Phòng đôi", "", true,
                        RoomStatus.TRONG_SACH, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Số phòng 502 đã tồn tại trong hệ thống.");

        verify(roomNoteRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateNonUniqueIntegrityViolation_isRethrownWithoutDuplicateMessage() {
        Room room = new Room();
        room.setId(501L);
        room.setRoomNumber("501");
        room.setFloor(5);
        room.setRoomType("Phòng đôi");
        room.setStatus(RoomStatus.TRONG_SACH);
        room.setActive(true);
        DataIntegrityViolationException integrityViolation =
                new DataIntegrityViolationException("room check constraint failed");

        when(roomRepository.findById(501L)).thenReturn(Optional.of(room));
        when(roomRepository.existsByRoomNumberIgnoreCaseAndIdNot("502", 501L)).thenReturn(false);
        when(bookingImpactChecker.check(eq(501L), anyString(), anyString(), anyString(), anyString(),
                eq(5), eq(5), any()))
                .thenReturn(new FutureBookingImpactChecker.FutureBookingImpact(true, 0, ""));
        when(roomRepository.saveAndFlush(any(Room.class))).thenThrow(integrityViolation);

        assertThatThrownBy(() -> roomManagementService.update(501L,
                new UpdatePhysicalRoomRequest("502", 5, "Phòng đôi", "", true,
                        RoomStatus.TRONG_SACH, true)))
                .isSameAs(integrityViolation)
                .hasMessage("room check constraint failed");

        verify(roomNoteRepository, org.mockito.Mockito.never()).save(any());
    }
    
    private static Room room(RoomStatus status) {
        Room room = new Room();
        room.setId(601L);
        room.setRoomNumber("601");
        room.setFloor(6);
        room.setRoomType("Phòng đôi");
        room.setStatus(status);
        room.setActive(true);
        return room;
    }

    @Test
    void createWithMaintenanceStatus_isRejectedWithGuidance() {
        when(roomRepository.existsByRoomNumberIgnoreCase("601")).thenReturn(false);

        assertThatThrownBy(() -> roomManagementService.create(
                new CreatePhysicalRoomRequest("601", 6, "Phòng đôi", "", true, RoomStatus.BAO_TRI)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(RoomManagementService.USE_MAINTENANCE_FORM_MESSAGE);

        verify(roomRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void createWithNote_savesNote() {
        when(roomRepository.existsByRoomNumberIgnoreCase("601")).thenReturn(false);
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roomNoteRepository.findByRoomId(any())).thenReturn(Optional.empty());

        roomManagementService.create(
                new CreatePhysicalRoomRequest("601", 6, "Phòng đôi", "  Cửa sổ bị kẹt  ", true, RoomStatus.TRONG_SACH));

        org.mockito.ArgumentCaptor<RoomNote> saved = org.mockito.ArgumentCaptor.forClass(RoomNote.class);
        verify(roomNoteRepository).save(saved.capture());
        assertThat(saved.getValue().getNote()).isEqualTo("Cửa sổ bị kẹt");
    }

    @Test
    void updateToMaintenanceFromOtherStatus_isRejectedWithGuidance() {
        when(roomRepository.findById(601L)).thenReturn(Optional.of(room(RoomStatus.TRONG_SACH)));
        when(roomRepository.existsByRoomNumberIgnoreCaseAndIdNot("601", 601L)).thenReturn(false);

        assertThatThrownBy(() -> roomManagementService.update(601L,
                new UpdatePhysicalRoomRequest("601", 6, "Phòng đôi", "", true, RoomStatus.BAO_TRI, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(RoomManagementService.USE_MAINTENANCE_FORM_MESSAGE);

        verify(roomRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void updateOutOfMaintenance_clearsMaintenanceDetails() {
        Room maintained = room(RoomStatus.BAO_TRI);
        maintained.setMaintenanceReason("Sửa điều hoà");
        maintained.setMaintenanceStartDate(java.time.LocalDate.of(2026, 10, 10));
        maintained.setMaintenanceEndDate(java.time.LocalDate.of(2026, 10, 13));
        when(roomRepository.findById(601L)).thenReturn(Optional.of(maintained));
        when(roomRepository.existsByRoomNumberIgnoreCaseAndIdNot("601", 601L)).thenReturn(false);
        when(bookingImpactChecker.check(eq(601L), anyString(), anyString(), anyString(), anyString(),
                eq(6), eq(6), any()))
                .thenReturn(new FutureBookingImpactChecker.FutureBookingImpact(false, 0, "Chưa kiểm tra được"));
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        roomManagementService.update(601L,
                new UpdatePhysicalRoomRequest("601", 6, "Phòng đôi", "", true, RoomStatus.TRONG_SACH, false));

        assertThat(maintained.getStatus()).isEqualTo(RoomStatus.TRONG_SACH);
        assertThat(maintained.getMaintenanceReason()).isNull();
        assertThat(maintained.getMaintenanceStartDate()).isNull();
        assertThat(maintained.getMaintenanceEndDate()).isNull();
    }

    @Test
    void updateOnlyNote_whenBookingCheckUnavailable_savesWithoutAskingForConfirmation() {
        when(roomRepository.findById(601L)).thenReturn(Optional.of(room(RoomStatus.TRONG_SACH)));
        when(roomRepository.existsByRoomNumberIgnoreCaseAndIdNot("601", 601L)).thenReturn(false);
        when(bookingImpactChecker.check(eq(601L), anyString(), anyString(), anyString(), anyString(),
                eq(6), eq(6), any()))
                .thenReturn(new FutureBookingImpactChecker.FutureBookingImpact(false, 0, "Chưa kiểm tra được"));
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roomNoteRepository.findByRoomId(any())).thenReturn(Optional.empty());

        var result = roomManagementService.update(601L,
                new UpdatePhysicalRoomRequest("601", 6, "Phòng đôi", "Bồn cầu rò nước", true,
                        RoomStatus.TRONG_SACH, false));

        // Frontend chỉ hiện "Cảnh báo trước khi lưu" khi warningRequired = true
        assertThat(result.warningRequired()).isFalse();
        assertThat(result.note()).isEqualTo("Bồn cầu rò nước");
        verify(roomNoteRepository).save(any(RoomNote.class));
    }
}