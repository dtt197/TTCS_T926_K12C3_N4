package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.pricing.PriceOverrideRequest;
import com.ttcs.homestay.dto.pricing.PriceOverrideResponse;
import com.ttcs.homestay.entity.PriceOverride;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.InvalidPriceOverrideException;
import com.ttcs.homestay.repository.PriceOverrideRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** S2-02 Lát 1: thêm, xem, sửa, xoá đợt giá đè (đúng 4 trường hợp test trong Jira + kiểm tra dữ liệu). */
@ExtendWith(MockitoExtension.class)
class PriceOverrideServiceTest {

    @Mock
    private PriceOverrideRepository priceOverrideRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @InjectMocks
    private PriceOverrideService priceOverrideService;

    @Test
    void danhSach_khongCoGiaDe_traVeDanhSachTrong() {
        when(priceOverrideRepository.findAllByOrderByStartDateAscIdAsc()).thenReturn(List.of());

        assertThat(priceOverrideService.listPriceOverrides()).isEmpty();
    }

    @Test
    void taoGiaDe_dayDuThongTin_hienTrongDanhSach() {
        RoomType doi = roomType(1L, "DOI", "Phòng đôi");
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(doi));
        when(priceOverrideRepository.save(any(PriceOverride.class))).thenAnswer(invocation -> {
            PriceOverride saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        PriceOverrideResponse created = priceOverrideService.createPriceOverride(
                request("Lễ 30/4", 1L, LocalDate.of(2026, 4, 29), LocalDate.of(2026, 5, 1), 900_000L), "Dinh Ba Chu");

        assertThat(created.name()).isEqualTo("Lễ 30/4");
        assertThat(created.roomTypeName()).isEqualTo("Phòng đôi");
        assertThat(created.nights()).isEqualTo(3);
        assertThat(created.pricePerNight()).isEqualTo(900_000L);
        assertThat(created.createdByName()).isEqualTo("Dinh Ba Chu");
    }

    @Test
    void suaGiaDe_capNhatThongTin() {
        RoomType doi = roomType(1L, "DOI", "Phòng đôi");
        PriceOverride existing = priceOverride(10L, "Lễ 30/4", doi, 900_000L);
        when(priceOverrideRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(doi));

        PriceOverrideResponse updated = priceOverrideService.updatePriceOverride(10L,
                request("Lễ 30/4 – 1/5", 1L, LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 2), 1_000_000L));

        assertThat(updated.name()).isEqualTo("Lễ 30/4 – 1/5");
        assertThat(updated.pricePerNight()).isEqualTo(1_000_000L);
        assertThat(existing.getStartDate()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void xoaGiaDe_bienMatKhoiDanhSach() {
        PriceOverride existing = priceOverride(10L, "Lễ 30/4", roomType(1L, "DOI", "Phòng đôi"), 900_000L);
        when(priceOverrideRepository.findById(10L)).thenReturn(Optional.of(existing));

        priceOverrideService.deletePriceOverride(10L);

        verify(priceOverrideRepository).delete(existing);
    }

    @Test
    void ngayKetThucTruocNgayBatDau_biChan() {
        assertThatThrownBy(() -> priceOverrideService.createPriceOverride(
                request("Sai ngày", 1L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 29), 900_000L), "Dinh Ba Chu"))
                .isInstanceOf(InvalidPriceOverrideException.class)
                .hasMessageContaining("không được trước ngày bắt đầu");
        verify(priceOverrideRepository, never()).save(any());
    }

    @Test
    void loaiPhongKhongTonTai_biChan() {
        when(roomTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> priceOverrideService.createPriceOverride(
                request("Lễ 30/4", 99L, LocalDate.of(2026, 4, 29), LocalDate.of(2026, 5, 1), 900_000L), "Dinh Ba Chu"))
                .isInstanceOf(InvalidPriceOverrideException.class)
                .hasMessageContaining("Loại phòng không tồn tại");
    }

    @Test
    void dotQuaDai_biChan() {
        assertThatThrownBy(() -> priceOverrideService.createPriceOverride(
                request("Cả năm", 1L, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 5), 900_000L), "Dinh Ba Chu"))
                .isInstanceOf(InvalidPriceOverrideException.class)
                .hasMessageContaining("366 đêm");
    }

    private static PriceOverrideRequest request(String name, Long roomTypeId, LocalDate start, LocalDate end,
            Long price) {
        return new PriceOverrideRequest(name, roomTypeId, start, end, price);
    }

    private static RoomType roomType(Long id, String code, String name) {
        RoomType roomType = new RoomType();
        roomType.setId(id);
        roomType.setCode(code);
        roomType.setName(name);
        return roomType;
    }

    private static PriceOverride priceOverride(Long id, String name, RoomType roomType, long price) {
        PriceOverride priceOverride = new PriceOverride();
        priceOverride.setId(id);
        priceOverride.setName(name);
        priceOverride.setRoomType(roomType);
        priceOverride.setStartDate(LocalDate.of(2026, 4, 29));
        priceOverride.setEndDate(LocalDate.of(2026, 5, 1));
        priceOverride.setPricePerNight(price);
        priceOverride.setCreatedByName("Dinh Ba Chu");
        return priceOverride;
    }
}