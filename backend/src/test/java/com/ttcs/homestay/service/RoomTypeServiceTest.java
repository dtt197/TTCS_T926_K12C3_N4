package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.roomtype.RoomTypeRequest;
import com.ttcs.homestay.dto.roomtype.RoomTypeResponse;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.InvalidRoomTypeCapacityException;
import com.ttcs.homestay.exception.RoomTypeConflictException;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomTypeServiceTest {

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private RoomTypeService roomTypeService;

    @Test
    void taoLoaiPhong_hopLe_maDuocLuuInHoaVaDangBan() {
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomTypeResponse response = roomTypeService.createRoomType(request("vip", "Phòng VIP", 2, 4));

        assertThat(response.code()).isEqualTo("VIP");
        assertThat(response.name()).isEqualTo("Phòng VIP");
        assertThat(response.active()).isTrue();
        assertThat(response.roomCount()).isZero();
    }

    @Test
    void taoLoaiPhong_sucChuaToiDaNhoHonTieuChuan_biChan() {
        // AC2
        assertThatThrownBy(() -> roomTypeService.createRoomType(request("DOI", "Phòng đôi", 3, 2)))
                .isInstanceOf(InvalidRoomTypeCapacityException.class)
                .hasMessageContaining("không được nhỏ hơn");
        verify(roomTypeRepository, never()).save(any());
    }

    @Test
    void taoLoaiPhong_trungMaKhacHoaThuong_biTuChoi() {
        // AC3: "doi" trùng với "DOI" đã có
        when(roomTypeRepository.existsByCodeIgnoreCase("DOI")).thenReturn(true);

        assertThatThrownBy(() -> roomTypeService.createRoomType(request("doi", "Phòng đôi mới", 2, 3)))
                .isInstanceOf(RoomTypeConflictException.class)
                .hasMessageContaining("DOI");
        verify(roomTypeRepository, never()).save(any());
    }

    @Test
    void taoLoaiPhong_trungTen_biTuChoi() {
        when(roomTypeRepository.existsByNameIgnoreCase("Phòng đôi")).thenReturn(true);

        assertThatThrownBy(() -> roomTypeService.createRoomType(request("DOI2", "Phòng đôi", 2, 3)))
                .isInstanceOf(RoomTypeConflictException.class)
                .hasMessageContaining("Phòng đôi");
    }

    @Test
    void xoaLoaiPhong_dangCoPhongGanVao_biChanVaGoiYNgungBan() {
        // AC4
        RoomType roomType = roomType(1L, "DOI", "Phòng đôi");
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(roomType));
        when(roomRepository.countByRoomTypeIgnoreCase("Phòng đôi")).thenReturn(2L);

        assertThatThrownBy(() -> roomTypeService.deleteRoomType(1L))
                .isInstanceOf(RoomTypeConflictException.class)
                .hasMessageContaining("2 phòng")
                .hasMessageContaining("ngừng bán");
        verify(roomTypeRepository, never()).delete(any());
    }

    @Test
    void xoaLoaiPhong_khongCoPhong_xoaDuoc() {
        RoomType roomType = roomType(2L, "SUITE", "Phòng suite");
        when(roomTypeRepository.findById(2L)).thenReturn(Optional.of(roomType));

        roomTypeService.deleteRoomType(2L);

        verify(roomTypeRepository).delete(roomType);
    }

    @Test
    void ngungBan_chuyenTrangThaiSangKhongBan() {
        // AC4: không xoá được thì đánh dấu ngừng bán
        RoomType roomType = roomType(1L, "DOI", "Phòng đôi");
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(roomType));

        RoomTypeResponse response = roomTypeService.updateStatus(1L, false);

        assertThat(response.active()).isFalse();
    }

    @Test
    void doiTenLoaiPhong_capNhatTenLoaiPhongTrongCacPhong() {
        RoomType roomType = roomType(1L, "DOI", "Phòng đôi");
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(roomType));

        roomTypeService.updateRoomType(1L, request("DOI", "Phòng đôi cao cấp", 2, 3));

        verify(roomRepository).renameRoomType("Phòng đôi", "Phòng đôi cao cấp");
        assertThat(roomType.getName()).isEqualTo("Phòng đôi cao cấp");
    }

    @Test
    void suaLoaiPhong_trungMaVoiLoaiPhongKhac_biTuChoi() {
        RoomType roomType = roomType(1L, "DOI", "Phòng đôi");
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(roomType));
        when(roomTypeRepository.existsByCodeIgnoreCaseAndIdNot("DON", 1L)).thenReturn(true);

        assertThatThrownBy(() -> roomTypeService.updateRoomType(1L, request("don", "Phòng đôi", 2, 3)))
                .isInstanceOf(RoomTypeConflictException.class);
        verify(roomRepository, never()).renameRoomType(anyString(), anyString());
    }

    private static RoomTypeRequest request(String code, String name, int standardCapacity, int maxCapacity) {
        return new RoomTypeRequest(code, name, standardCapacity, maxCapacity, 1, null, null);
    }

    private static RoomType roomType(Long id, String code, String name) {
        RoomType roomType = new RoomType();
        roomType.setId(id);
        roomType.setCode(code);
        roomType.setName(name);
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(3);
        roomType.setNumberOfBeds(1);
        roomType.setStatus(true);
        return roomType;
    }
}