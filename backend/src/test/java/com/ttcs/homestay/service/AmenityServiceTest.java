package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.amenity.AmenityRequest;
import com.ttcs.homestay.dto.amenity.AmenityResponse;
import com.ttcs.homestay.entity.Amenity;
import com.ttcs.homestay.exception.AmenityConflictException;
import com.ttcs.homestay.repository.AmenityRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AmenityServiceTest {

    @Mock
    private AmenityRepository amenityRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @InjectMocks
    private AmenityService amenityService;

    @Test
    void taoTienNghi_hopLe_maDuocLuuInHoaVaDangDung() {
        // AC1
        when(amenityRepository.save(any(Amenity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AmenityResponse response = amenityService.createAmenity(new AmenityRequest("may_say", "Máy sấy tóc", "💨"));

        assertThat(response.code()).isEqualTo("MAY_SAY");
        assertThat(response.icon()).isEqualTo("💨");
        assertThat(response.active()).isTrue();
    }

    @Test
    void taoTienNghi_trungMaKhacHoaThuong_biTuChoi() {
        when(amenityRepository.existsByCodeIgnoreCase("WIFI")).thenReturn(true);

        assertThatThrownBy(() -> amenityService.createAmenity(new AmenityRequest("wifi", "Wi-Fi", "📶")))
                .isInstanceOf(AmenityConflictException.class)
                .hasMessageContaining("WIFI");
        verify(amenityRepository, never()).save(any());
    }

    @Test
    void xoaTienNghi_dangGanChoLoaiPhong_biChanVaGoiYNgungDung() {
        // AC3
        Amenity amenity = amenity(1L, "DIEU_HOA", "Điều hoà", true);
        when(amenityRepository.findById(1L)).thenReturn(Optional.of(amenity));
        when(roomTypeRepository.countByAmenitiesId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> amenityService.deleteAmenity(1L))
                .isInstanceOf(AmenityConflictException.class)
                .hasMessageContaining("3 loại phòng")
                .hasMessageContaining("ngừng dùng");
        verify(amenityRepository, never()).delete(any());
    }

    @Test
    void xoaTienNghi_chuaGanChoLoaiPhongNao_xoaDuoc() {
        Amenity amenity = amenity(2L, "BON_TAM", "Bồn tắm", true);
        when(amenityRepository.findById(2L)).thenReturn(Optional.of(amenity));

        amenityService.deleteAmenity(2L);

        verify(amenityRepository).delete(amenity);
    }

    @Test
    void ngungDung_chuyenTrangThai() {
        Amenity amenity = amenity(1L, "DIEU_HOA", "Điều hoà", true);
        when(amenityRepository.findById(1L)).thenReturn(Optional.of(amenity));

        AmenityResponse response = amenityService.updateStatus(1L, false);

        assertThat(response.active()).isFalse();
    }

    static Amenity amenity(Long id, String code, String name, boolean active) {
        Amenity amenity = new Amenity();
        amenity.setId(id);
        amenity.setCode(code);
        amenity.setName(name);
        amenity.setIcon("✨");
        amenity.setActive(active);
        return amenity;
    }
}