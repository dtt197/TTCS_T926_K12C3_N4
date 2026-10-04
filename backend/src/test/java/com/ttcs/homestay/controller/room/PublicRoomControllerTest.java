package com.ttcs.homestay.controller.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.RoomTypeRepository;
import com.ttcs.homestay.service.RoomAvailabilityService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PublicRoomControllerTest {

    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(10);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(3);

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private RoomAvailabilityService roomAvailabilityService;

    @InjectMocks
    private PublicRoomController controller;

    @Test
    void searchChiTraVeLoaiDuSucChuaVaConPhong() {
        RoomType tooSmall = roomType(1L, "Phòng đơn", 2);
        RoomType available = roomType(2L, "Phòng đôi", 3);
        RoomType full = roomType(3L, "Phòng gia đình", 5);
        RoomType stopped = roomType(4L, "Phòng ngừng bán", 5);
        stopped.setStatus(false);
        when(roomTypeRepository.findAll()).thenReturn(List.of(tooSmall, available, full, stopped));
        when(roomAvailabilityService.availableRooms(available, CHECK_IN, CHECK_OUT)).thenReturn(1);
        when(roomAvailabilityService.availableRooms(full, CHECK_IN, CHECK_OUT)).thenReturn(0);

        ResponseEntity<?> response =
                controller.searchAvailableRooms(CHECK_IN, CHECK_OUT, 3);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .isInstanceOf(List.class)
                .asList()
                .containsExactly(new PublicRoomController.RoomAvailabilityResponse(
                        2L, "Phòng đôi", 3, 500000, 1
                ));
        verify(roomAvailabilityService, never())
                .availableRooms(tooSmall, CHECK_IN, CHECK_OUT);
        verify(roomAvailabilityService, never())
                .availableRooms(stopped, CHECK_IN, CHECK_OUT);
    }

    @Test
    void searchKhongCoLoaiPhongPhuHopTraVeDanhSachRong() {
        when(roomTypeRepository.findAll()).thenReturn(List.of(roomType(1L, "Phòng đơn", 2)));

        ResponseEntity<?> response =
                controller.searchAvailableRooms(CHECK_IN, CHECK_OUT, 3);

        assertThat(response.getBody()).isEqualTo(List.of());
    }

    @Test
    void searchTuMayChuTuChoiNgayKhongHopLe() {
        assertThatThrownBy(() -> controller.searchAvailableRooms(CHECK_IN, CHECK_IN, 2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ngày trả phòng phải sau");

        assertThatThrownBy(() -> controller.searchAvailableRooms(CHECK_IN, CHECK_IN.plusDays(31), 2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("không được vượt quá 30 đêm");

        assertThatThrownBy(() -> controller.searchAvailableRooms(CHECK_IN, CHECK_OUT, 0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Số lượng khách");

        assertThatThrownBy(() -> controller.searchAvailableRooms(
                        LocalDate.now().minusDays(1),
                        LocalDate.now().plusDays(1),
                        2
                ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("không được trước ngày hiện tại");
    }

    private static RoomType roomType(long id, String name, int maxCapacity) {
        RoomType roomType = new RoomType();
        roomType.setId(id);
        roomType.setName(name);
        roomType.setMaxCapacity(maxCapacity);
        roomType.setWeekdayPrice(500000L);
        return roomType;
    }
}
