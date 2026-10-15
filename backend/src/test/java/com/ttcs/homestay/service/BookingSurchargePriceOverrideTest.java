package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ttcs.homestay.dto.booking.BookingSurchargeListResponse;
import com.ttcs.homestay.dto.booking.BookingSurchargeRequest;
import com.ttcs.homestay.dto.booking.BookingSurchargeResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.SurchargeType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** S4-02 Lát 4: sửa tay đơn giá phụ thu, bắt buộc nhập lý do. */
@SpringBootTest
@Transactional
class BookingSurchargePriceOverrideTest {

    @Autowired private BookingSurchargeService surchargeService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;

    private Booking booking;

    @BeforeEach
    void setUp() {
        RoomType roomType = new RoomType();
        roomType.setCode("S402PRC");
        roomType.setName("Phòng thử sửa giá");
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(6);
        roomType.setNumberOfBeds(2);
        roomType.setStatus(true);
        roomType.setWeekdayPrice(500_000L);
        roomType.setWeekendPrice(700_000L);
        roomType.setExtraGuestFee(150_000L);
        roomType = roomTypeRepository.save(roomType);

        booking = new Booking();
        booking.setBookingCode("S402PRC1");
        booking.setGuestName("Khách sửa giá");
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setRoomType(roomType);
        booking.setRoomTypeNameSnapshot(roomType.getName());
        booking.setCheckInDate(LocalDate.of(2027, 6, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 6, 13));
        booking.setWeekdayPriceSnapshot(500_000L);
        booking.setWeekendPriceSnapshot(700_000L);
        booking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        booking.setTotalAmount(1_500_000L);
        booking.setGuestCount(1);
        booking.setCreatedAt(OffsetDateTime.now());
        booking = bookingRepository.saveAndFlush(booking);
    }

    private static BookingSurchargeRequest themNguoi(Long unitPrice, String reason) {
        return new BookingSurchargeRequest(SurchargeType.THEM_NGUOI, 1, 2, null, unitPrice, reason);
    }

    private static HttpStatus statusOf(Throwable error) {
        return HttpStatus.valueOf(((ResponseStatusException) error).getStatusCode().value());
    }

    @Test
    void giuNguyenGiaMacDinh_khongCanLyDo() {
        BookingSurchargeListResponse first = surchargeService.addSurcharge(booking.getId(), themNguoi(null, null));
        BookingSurchargeListResponse second = surchargeService.addSurcharge(booking.getId(), themNguoi(150_000L, null));

        assertThat(first.items().get(0).priceOverridden()).isFalse();
        assertThat(first.items().get(0).defaultUnitPrice()).isEqualTo(150_000L);
        assertThat(second.items().get(1).priceOverridden()).isFalse();
        assertThat(second.items().get(1).amount()).isEqualTo(300_000L);
    }

    @Test
    void suaGiaKemLyDo_luuDuoc_thanhTienTheoGiaMoi() {
        BookingSurchargeListResponse result = surchargeService.addSurcharge(booking.getId(),
                themNguoi(100_000L, "  Khách quen, chủ đồng ý giảm  "));

        BookingSurchargeResponse line = result.items().get(0);
        assertThat(line.unitPrice()).isEqualTo(100_000L);
        assertThat(line.defaultUnitPrice()).isEqualTo(150_000L);
        assertThat(line.amount()).isEqualTo(200_000L);
        assertThat(line.priceOverridden()).isTrue();
        assertThat(line.priceOverrideReason()).isEqualTo("Khách quen, chủ đồng ý giảm");
        assertThat(line.priceOverriddenBy()).isNotBlank();
        assertThat(result.totalAmount()).isEqualTo(200_000L);
    }

    @Test
    void suaGiaBoTrongLyDo_biTuChoi() {
        assertThatThrownBy(() -> surchargeService.addSurcharge(booking.getId(), themNguoi(100_000L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusOf(error)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("lý do sửa giá");
        assertThat(surchargeService.getSurcharges(booking.getId()).items()).isEmpty();
    }

    @Test
    void lyDoChiGomDauCach_biTuChoi() {
        assertThatThrownBy(() -> surchargeService.addSurcharge(booking.getId(), themNguoi(100_000L, "   ")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("lý do sửa giá");
    }

    @Test
    void donGiaAm_biTuChoi() {
        assertThatThrownBy(() -> surchargeService.addSurcharge(booking.getId(), themNguoi(-1_000L, "Thử giá âm")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusOf(error)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Đơn giá phải từ 0");
    }

    @Test
    void donGiaVuotGioiHan_biTuChoi() {
        assertThatThrownBy(() -> surchargeService.addSurcharge(booking.getId(), themNguoi(100_000_001L, "Thử giá quá lớn")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("100.000.000");
    }
}