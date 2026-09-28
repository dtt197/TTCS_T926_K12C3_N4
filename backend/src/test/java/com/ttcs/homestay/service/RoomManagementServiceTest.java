package com.ttcs.homestay.service;

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

import com.ttcs.homestay.dto.UpdatePhysicalRoomRequest;
import com.ttcs.homestay.entity.Room;
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
}