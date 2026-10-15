package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ttcs.homestay.dto.booking.BookingSurchargeListResponse;
import com.ttcs.homestay.dto.booking.BookingSurchargeRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.SurchargeType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** S4-02 Lát 1: thêm dòng phụ thu thêm người / thêm giường với đơn giá mặc định. */
@SpringBootTest
@Transactional
class BookingSurchargeServiceTest {

    @Autowired private BookingSurchargeService surchargeService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private OperatingSettingsRepository operatingSettingsRepository;

    private RoomType roomType;
    private Booking booking;

    @BeforeEach
    void setUp() {
        saveSettingsWithExtraBedFee(100_000L);

        roomType = new RoomType();
        roomType.setCode("S402SUR");
        roomType.setName("Phòng thử S4-02");
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(4);
        roomType.setNumberOfBeds(1);
        roomType.setStatus(true);
        roomType.setWeekdayPrice(500_000L);
        roomType.setWeekendPrice(700_000L);
        roomType.setExtraGuestFee(150_000L);
        roomType = roomTypeRepository.save(roomType);

        booking = saveBooking("S402BK1", BookingStatus.DA_NHAN_PHONG);
    }

    private Booking saveBooking(String code, BookingStatus status) {
        Booking item = new Booking();
        item.setBookingCode(code);
        item.setGuestName("Khách phụ thu");
        item.setStatus(status);
        item.setRoomType(roomType);
        item.setRoomTypeNameSnapshot(roomType.getName());
        item.setCheckInDate(LocalDate.of(2027, 6, 10));
        item.setCheckOutDate(LocalDate.of(2027, 6, 13)); // 3 đêm
        item.setWeekdayPriceSnapshot(500_000L);
        item.setWeekendPriceSnapshot(700_000L);
        item.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        item.setTotalAmount(1_500_000L);
        item.setCreatedAt(OffsetDateTime.now());
        return bookingRepository.saveAndFlush(item);
    }

    /** Tham số có lịch sử: tạo phiên bản mới nhất thay vì sửa đè. */
    private void saveSettingsWithExtraBedFee(long extraBedFee) {
        OffsetDateTime createdAt = operatingSettingsRepository.findFirstByOrderByCreatedAtDescIdDesc()
                .map(latest -> latest.getCreatedAt().plusSeconds(1))
                .orElse(OffsetDateTime.now().minusDays(1));
        OperatingSettings settings = new OperatingSettings();
        settings.setHomestayName("HomeStay Test");
        settings.setCheckInTime(LocalTime.of(14, 0));
        settings.setCheckOutTime(LocalTime.of(12, 0));
        settings.setLateCheckoutFeePerHour(100_000L);
        settings.setExtraPersonFee(200_000L);
        settings.setExtraBedFee(extraBedFee);
        settings.setCreatedByName("Hệ thống");
        settings.setCreatedAt(createdAt);
        settings.setWeekendDays("FRIDAY,SATURDAY");
        operatingSettingsRepository.saveAndFlush(settings);
    }

    private static BookingSurchargeRequest request(SurchargeType type, int quantity, int nights) {
        return new BookingSurchargeRequest(type, quantity, nights, null);
    }

    private static HttpStatus statusOf(Throwable error) {
        return HttpStatus.valueOf(((ResponseStatusException) error).getStatusCode().value());
    }

    @Test
    void themMotDongThemNguoi_layDonGiaMacDinhCuaLoaiPhong() {
        BookingSurchargeListResponse result = surchargeService.addSurcharge(
                booking.getId(), new BookingSurchargeRequest(SurchargeType.THEM_NGUOI, 1, 2, "Thêm bạn"));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).type()).isEqualTo(SurchargeType.THEM_NGUOI);
        assertThat(result.items().get(0).unitPrice()).isEqualTo(150_000L);
        assertThat(result.items().get(0).amount()).isEqualTo(300_000L);
        assertThat(result.items().get(0).note()).isEqualTo("Thêm bạn");
        assertThat(result.items().get(0).createdBy()).isNotBlank();
        assertThat(result.items().get(0).createdAt()).isNotNull();
        assertThat(result.totalAmount()).isEqualTo(300_000L);
    }

    @Test
    void themTiepDongThemGiuong_bangCoHaiDongVaTongCongDung() {
        surchargeService.addSurcharge(booking.getId(), request(SurchargeType.THEM_NGUOI, 1, 2));
        BookingSurchargeListResponse result =
                surchargeService.addSurcharge(booking.getId(), request(SurchargeType.THEM_GIUONG, 1, 1));

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(1).type()).isEqualTo(SurchargeType.THEM_GIUONG);
        assertThat(result.items().get(1).unitPrice()).isEqualTo(100_000L);
        assertThat(result.items().get(1).amount()).isEqualTo(100_000L);
        assertThat(result.totalAmount()).isEqualTo(400_000L);
    }

    @Test
    void thanhTienDung_soLuong2Va3Dem() {
        BookingSurchargeListResponse result =
                surchargeService.addSurcharge(booking.getId(), request(SurchargeType.THEM_NGUOI, 2, 3));

        assertThat(result.items().get(0).amount()).isEqualTo(2 * 150_000L * 3);
        assertThat(result.totalAmount()).isEqualTo(900_000L);
    }

    @Test
    void soLuong0_biTuChoi() {
        assertThatThrownBy(() -> surchargeService.addSurcharge(booking.getId(), request(SurchargeType.THEM_NGUOI, 0, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusOf(error)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Số lượng phải từ 1 trở lên");
    }

    @Test
    void soDemVuotMuc_biTuChoi() {
        assertThatThrownBy(() -> surchargeService.addSurcharge(booking.getId(), request(SurchargeType.THEM_GIUONG, 1, 4)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusOf(error)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("không được vượt quá 3 đêm");

        assertThatThrownBy(() -> surchargeService.addSurcharge(booking.getId(), request(SurchargeType.THEM_GIUONG, 1, 0)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusOf(error)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void bookingDaHuy_khongThemDuoc() {
        Booking cancelled = saveBooking("S402BK2", BookingStatus.DA_HUY);

        assertThatThrownBy(() -> surchargeService.addSurcharge(cancelled.getId(), request(SurchargeType.THEM_NGUOI, 1, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusOf(error)).isEqualTo(HttpStatus.CONFLICT));
        assertThat(surchargeService.getSurcharges(cancelled.getId()).canAddSurcharge()).isFalse();
        assertThat(surchargeService.getSurcharges(cancelled.getId()).items()).isEmpty();
    }

    @Test
    void doiThamSoDonGia_dongSauLayGiaMoi_dongCuGiuGiaCu() {
        surchargeService.addSurcharge(booking.getId(), request(SurchargeType.THEM_GIUONG, 1, 1));

        saveSettingsWithExtraBedFee(120_000L);
        BookingSurchargeListResponse result =
                surchargeService.addSurcharge(booking.getId(), request(SurchargeType.THEM_GIUONG, 1, 1));

        assertThat(result.items().get(0).unitPrice()).isEqualTo(100_000L);
        assertThat(result.items().get(1).unitPrice()).isEqualTo(120_000L);
        assertThat(result.defaultExtraBedPrice()).isEqualTo(120_000L);
        assertThat(result.totalAmount()).isEqualTo(220_000L);
    }
}