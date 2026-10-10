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
import com.ttcs.homestay.entity.Amenity;
import com.ttcs.homestay.repository.AmenityRepository;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.InvalidRoomTypeCapacityException;
import com.ttcs.homestay.exception.RoomTypeConflictException;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.util.Optional;
import java.util.List;
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
    
    @Mock
    private AmenityRepository amenityRepository;

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
    void taoLoaiPhong_luuPhuThuThemNguoiRiengTheoLoai() {
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RoomTypeRequest request = new RoomTypeRequest(
                "DON", "Phòng đơn", 1, 2, 1, null, true, null, 300_000L, 400_000L, 150_000L);

        RoomTypeResponse response = roomTypeService.createRoomType(request);

        assertThat(response.extraGuestFee()).isEqualTo(150_000L);
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
        @Test
    void ganTienNghi_hopLe_themVaoLoaiPhong() {
        RoomType roomType = roomType(1L, "DOI", "Phòng đôi");
        Amenity wifi = AmenityServiceTest.amenity(10L, "WIFI", "Wi-Fi", true);
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(roomType));
        when(amenityRepository.findById(10L)).thenReturn(Optional.of(wifi));

        RoomTypeResponse response = roomTypeService.addAmenity(1L, 10L);

        assertThat(response.amenities())        .extracting(amenity -> amenity.code()).containsExactly("WIFI");
    }

    @Test
    void ganTienNghi_trung_biChan() {
        // S1-08 AC2: gắn trùng cùng một tiện nghi bị chặn
        RoomType roomType = roomType(1L, "DOI", "Phòng đôi");
        Amenity wifi = AmenityServiceTest.amenity(10L, "WIFI", "Wi-Fi", true);
        roomType.getAmenities().add(wifi);
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(roomType));
        when(amenityRepository.findById(10L)).thenReturn(Optional.of(wifi));

        assertThatThrownBy(() -> roomTypeService.addAmenity(1L, 10L))
                .isInstanceOf(RoomTypeConflictException.class)
                .hasMessageContaining("đã có tiện nghi");
    }

    @Test
    void ganTienNghi_daNgungDung_biChan() {
        RoomType roomType = roomType(1L, "DOI", "Phòng đôi");
        Amenity tivi = AmenityServiceTest.amenity(11L, "TIVI", "Tivi", false);
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(roomType));
        when(amenityRepository.findById(11L)).thenReturn(Optional.of(tivi));

        assertThatThrownBy(() -> roomTypeService.addAmenity(1L, 11L))
                .isInstanceOf(RoomTypeConflictException.class)
                .hasMessageContaining("ngừng dùng");
    }

    @Test
    void danhSachLoaiPhong_anTienNghiDaNgungDung() {
        // S1-08 AC4
        RoomType roomType = roomType(1L, "DOI", "Phòng đôi");
        roomType.getAmenities().add(AmenityServiceTest.amenity(10L, "WIFI", "Wi-Fi", true));
        roomType.getAmenities().add(AmenityServiceTest.amenity(11L, "TIVI", "Tivi", false));

        RoomTypeResponse response = RoomTypeResponse.from(roomType, 0);

        assertThat(response.amenities())        .extracting(amenity -> amenity.code()).containsExactly("WIFI");
    }
    @Test
    void taoLoaiPhong_kemTienNghiDaTick_ganLuon() {
        Amenity wifi = AmenityServiceTest.amenity(10L, "WIFI", "Wi-Fi", true);
        Amenity tivi = AmenityServiceTest.amenity(11L, "TIVI", "Tivi", true);
        when(amenityRepository.findById(10L)).thenReturn(Optional.of(wifi));
        when(amenityRepository.findById(11L)).thenReturn(Optional.of(tivi));
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomTypeResponse response = roomTypeService.createRoomType(
                requestWithAmenities("SUITE", "Phòng suite", List.of(10L, 11L)));

        assertThat(response.amenities())        .extracting(amenity -> amenity.code()).containsExactlyInAnyOrder("WIFI", "TIVI");
    }

    @Test
    void suaLoaiPhong_boTickTienNghi_vanGiuTienNghiDaNgungDung() {
        RoomType roomType = roomType(1L, "DOI", "Phòng đôi");
        roomType.getAmenities().add(AmenityServiceTest.amenity(10L, "WIFI", "Wi-Fi", true));
        roomType.getAmenities().add(AmenityServiceTest.amenity(11L, "TIVI", "Tivi", true));
        roomType.getAmenities().add(AmenityServiceTest.amenity(12L, "QUAT", "Quạt cây", false));
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(roomType));

        roomTypeService.updateRoomType(1L, requestWithAmenities("DOI", "Phòng đôi", List.of(10L)));

        // Bỏ tick Tivi thì Tivi bị gỡ; Quạt cây đã ngừng dùng (không hiện trên giao diện) vẫn giữ.
        assertThat(roomType.getAmenities())        .extracting(amenity -> amenity.getCode()).containsExactlyInAnyOrder("WIFI", "QUAT");
    }

    @Test
    void taoLoaiPhong_tickTienNghiDaNgungDung_biChan() {
        Amenity tivi = AmenityServiceTest.amenity(11L, "TIVI", "Tivi", false);
        when(amenityRepository.findById(11L)).thenReturn(Optional.of(tivi));

        assertThatThrownBy(() -> roomTypeService.createRoomType(
                requestWithAmenities("SUITE", "Phòng suite", List.of(11L))))
                .isInstanceOf(RoomTypeConflictException.class)
                .hasMessageContaining("ngừng dùng");
        verify(roomTypeRepository, never()).save(any());
    }

  private static RoomTypeRequest requestWithAmenities(
        String code,
        String name,
        List<Long> amenityIds) {

    return new RoomTypeRequest(
            code,
            name,
            2,
            3,
            1,
            null,
            null,
            amenityIds,
            500000L,
            650000L
    );
}

private static RoomTypeRequest request(
        String code,
        String name,
        int standardCapacity,
        int maxCapacity) {

    return new RoomTypeRequest(
            code,
            name,
            standardCapacity,
            maxCapacity,
            1,
            null,
            null,
            null,
            500000L,
            650000L
    );
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
    @Test
void taoLoaiPhong_coGiaNgayThuong_luuDungGia() {
    when(roomTypeRepository.save(any(RoomType.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    RoomTypeResponse response =
            roomTypeService.createRoomType(
                    new RoomTypeRequest(
                            "VIP",
                            "Phòng VIP",
                            2,
                            4,
                            1,
                            null,
                            null,
                            null,
1200000L,
1500000L
                    )
            );

    assertThat(response.weekdayPrice())
            .isEqualTo(1200000L);
}

@Test
void suaLoaiPhong_capNhatGiaNgayThuong() {
    RoomType roomType =
            roomType(1L, "VIP", "Phòng VIP");

    roomType.setWeekdayPrice(1000000L);

    when(roomTypeRepository.findById(1L))
            .thenReturn(Optional.of(roomType));

    RoomTypeResponse response =
            roomTypeService.updateRoomType(
                    1L,
                    new RoomTypeRequest(
                            "VIP",
                            "Phòng VIP",
                            2,
                            4,
                            1,
                            null,
                            null,
                            null,
                            1200000L,
                            1500000L
                    )
            );

    assertThat(response.weekdayPrice())
            .isEqualTo(1200000L);
}
@Test
void taoLoaiPhong_coGiaCuoiTuan_luuDungGia() {
    when(roomTypeRepository.save(any(RoomType.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    RoomTypeResponse response =
            roomTypeService.createRoomType(
                    new RoomTypeRequest(
                            "VIP",
                            "Phòng VIP",
                            2,
                            4,
                            1,
                            null,
                            null,
                            null,
                            1200000L,
                            1500000L
                    )
            );

    assertThat(response.weekendPrice())
            .isEqualTo(1500000L);
}

@Test
void suaLoaiPhong_capNhatGiaCuoiTuan() {
    RoomType roomType =
            roomType(1L, "VIP", "Phòng VIP");

    roomType.setWeekdayPrice(1200000L);
    roomType.setWeekendPrice(1400000L);

    when(roomTypeRepository.findById(1L))
            .thenReturn(Optional.of(roomType));

    RoomTypeResponse response =
            roomTypeService.updateRoomType(
                    1L,
                    new RoomTypeRequest(
                            "VIP",
                            "Phòng VIP",
                            2,
                            4,
                            1,
                            null,
                            null,
                            null,
                            1200000L,
                            1600000L
                    )
            );

    assertThat(response.weekendPrice())
            .isEqualTo(1600000L);
}
}