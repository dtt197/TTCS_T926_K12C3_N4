package com.ttcs.homestay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcs.homestay.entity.PriceOverride;
import com.ttcs.homestay.entity.RoomType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * S2-02 Lát 3: câu truy vấn tìm đợt trùng ngày chạy trên CSDL thật (H2), mỗi test tự rollback.
 * Có sẵn đợt "Lễ 30/4" của Phòng đôi: 29/04/2027 – 01/05/2027.
 */
@SpringBootTest
@Transactional
class PriceOverrideRepositoryTest {

    @Autowired
    private PriceOverrideRepository priceOverrideRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    private RoomType doi;
    private RoomType don;

    @BeforeEach
    void setUp() {
        doi = roomTypeRepository.save(roomType("TEST_DOI", "Phòng đôi (test)"));
        don = roomTypeRepository.save(roomType("TEST_DON", "Phòng đơn (test)"));

        PriceOverride le304 = new PriceOverride();
        le304.setName("Lễ 30/4");
        le304.setRoomType(doi);
        le304.setStartDate(LocalDate.of(2027, 4, 29));
        le304.setEndDate(LocalDate.of(2027, 5, 1));
        le304.setPricePerNight(900_000L);
        le304.setCreatedByName("Test");
        le304.setCreatedAt(OffsetDateTime.now());
        le304.setUpdatedAt(OffsetDateTime.now());
        priceOverrideRepository.save(le304);
    }

    @Test
    void trungMotDem_timThayDotXungDot() {
        assertThat(priceOverrideRepository.findOverlapping(doi.getId(), LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 3)))
                .extracting(PriceOverride::getName)
                .containsExactly("Lễ 30/4");
    }

    @Test
    void baoTrumCaDot_timThayDotXungDot() {
        assertThat(priceOverrideRepository.findOverlapping(doi.getId(), LocalDate.of(2027, 4, 1), LocalDate.of(2027, 5, 31)))
                .hasSize(1);
    }

    @Test
    void dotLienKeTruocVaSau_khongTrung() {
        assertThat(priceOverrideRepository.findOverlapping(doi.getId(), LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 4)))
                .isEmpty();
        assertThat(priceOverrideRepository.findOverlapping(doi.getId(), LocalDate.of(2027, 4, 25), LocalDate.of(2027, 4, 28)))
                .isEmpty();
    }

    @Test
    void cungNgayNhungKhacLoaiPhong_khongTrung() {
        assertThat(priceOverrideRepository.findOverlapping(don.getId(), LocalDate.of(2027, 4, 29), LocalDate.of(2027, 5, 1)))
                .isEmpty();
    }

    private static RoomType roomType(String code, String name) {
        RoomType roomType = new RoomType();
        roomType.setCode(code);
        roomType.setName(name);
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(3);
        roomType.setNumberOfBeds(1);
        return roomType;
    }
}