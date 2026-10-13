package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ttcs.homestay.dto.booking.BookingChangePreviewResponse;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.BookingUpdateRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingDeposit;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingDepositRepository;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.math.BigDecimal;
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

/**
 * S3-04: sửa hai lỗi tester báo.
 * AC3: tiền cọc đã ghi nhận được giữ nguyên và trừ vào tổng tiền mới.
 * AC4: không đổi được sang khoảng ngày đã bắt đầu trong quá khứ.
 */
@SpringBootTest
@Transactional
class BookingUpdateDepositAndPastDateTest {

    private static final LocalDate OLD_CHECK_IN = LocalDate.of(2027, 6, 10);
    private static final LocalDate OLD_CHECK_OUT = LocalDate.of(2027, 6, 13);

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private OperatingSettingsRepository operatingSettingsRepository;

    @Autowired
    private BookingDepositRepository bookingDepositRepository;

    private RoomType roomType;
    private Booking booking;

    @BeforeEach
    void setUp() {
        operatingSettingsRepository.findAll().stream().findFirst().orElseGet(() -> {
            OperatingSettings settings = new OperatingSettings();
            settings.setHomestayName("HomeStay Test");
            settings.setCheckInTime(LocalTime.of(14, 0));
            settings.setCheckOutTime(LocalTime.of(12, 0));
            settings.setLateCheckoutFeePerHour(100_000L);
            settings.setExtraPersonFee(200_000L);
            settings.setCreatedByName("Hệ thống");
            settings.setCreatedAt(OffsetDateTime.now().minusDays(1));
            settings.setWeekendDays("FRIDAY,SATURDAY");
            return operatingSettingsRepository.save(settings);
        });

        roomType = new RoomType();
        roomType.setCode("S304FIX");
        roomType.setName("Phòng thử S3-04");
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(3);
        roomType.setNumberOfBeds(1);
        roomType.setStatus(true);
        roomType.setWeekdayPrice(500_000L);
        roomType.setWeekendPrice(700_000L);
        roomType = roomTypeRepository.save(roomType);

        Room room = new Room();
        room.setRoomNumber("R-S304FIX");
        room.setFloor(1);
        room.setRoomType(roomType.getName());
        room.setStatus(RoomStatus.TRONG_SACH);
        room.setActive(true);
        roomRepository.save(room);

        booking = new Booking();
        booking.setBookingCode("S304FIXBK1");
        booking.setGuestName("Khách có cọc");
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setRoomType(roomType);
        booking.setRoomTypeNameSnapshot(roomType.getName());
        booking.setCheckInDate(OLD_CHECK_IN);
        booking.setCheckOutDate(OLD_CHECK_OUT);
        booking.setWeekdayPriceSnapshot(500_000L);
        booking.setWeekendPriceSnapshot(700_000L);
        booking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        booking.setTotalAmount(1_500_000L);
        booking.setCreatedAt(OffsetDateTime.now());
        booking = bookingRepository.saveAndFlush(booking);
    }

    private void recordDeposit(long amount) {
        BookingDeposit deposit = new BookingDeposit();
        deposit.setBooking(booking);
        deposit.setAmount(BigDecimal.valueOf(amount));
        deposit.setPaymentMethod("CASH");
        deposit.setReceivedDate(LocalDate.of(2027, 6, 1));
        deposit.setCreatedBy("Lễ tân thử");
        bookingDepositRepository.saveAndFlush(deposit);
    }

    private BookingUpdateRequest change(LocalDate checkIn, LocalDate checkOut) {
        return new BookingUpdateRequest(roomType.getId(), checkIn, checkOut);
    }

    private static HttpStatus statusOf(Throwable error) {
        return HttpStatus.valueOf(((ResponseStatusException) error).getStatusCode().value());
    }

    @Test
    void bookingCoCoc_doiNgayDuoc_tienCocGiuNguyenVaTruVaoTongMoi() {
        recordDeposit(220_000L);
        BookingUpdateRequest request = change(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 5));

        BookingChangePreviewResponse preview = bookingService.previewBookingChange(booking.getId(), request);
        BookingResponse updated = bookingService.updateBooking(booking.getId(), request);

        assertThat(preview.oldTotalAmount()).isEqualTo(1_500_000L);
        assertThat(preview.totalDifference()).isEqualTo(preview.totalAmount() - 1_500_000L);
        assertThat(preview.depositAmount()).isEqualTo(220_000L);
        assertThat(preview.remainingAmount()).isEqualTo(preview.totalAmount() - 220_000L);
        assertThat(updated.checkInDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(updated.checkOutDate()).isEqualTo(LocalDate.of(2027, 7, 5));
        assertThat(updated.totalAmount()).isEqualTo(preview.totalAmount());
        // Bản ghi cọc không bị sửa hay nhân đôi.
        assertThat(bookingDepositRepository.findByBooking(booking)).isPresent();
        assertThat(bookingDepositRepository.findByBooking(booking).orElseThrow().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(220_000L));
    }

    @Test
    void bookingChuaCoCoc_conPhaiThuBangTongMoi() {
        BookingChangePreviewResponse preview = bookingService.previewBookingChange(
                booking.getId(), change(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3)));

        assertThat(preview.depositAmount()).isZero();
        assertThat(preview.remainingAmount()).isEqualTo(preview.totalAmount());
    }

    @Test
    void tienCocLonHonTongMoi_biTuChoiVaKhongDoiBooking() {
        recordDeposit(1_400_000L);

        assertThatThrownBy(() -> bookingService.updateBooking(
                booking.getId(), change(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 2))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusOf(error)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("điều chỉnh giảm cọc");

        Booking unchanged = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(unchanged.getCheckInDate()).isEqualTo(OLD_CHECK_IN);
        assertThat(unchanged.getTotalAmount()).isEqualTo(1_500_000L);
    }

    @Test
    void doiVeNgayQuaKhu_biTuChoiCaXemTruocVaLuu_khongDoiBooking() {
        LocalDate yesterday = PublicBookingDatePolicy.today().minusDays(1);
        BookingUpdateRequest request = change(yesterday, yesterday.plusDays(1));

        assertThatThrownBy(() -> bookingService.previewBookingChange(booking.getId(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusOf(error)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("quá khứ");
        assertThatThrownBy(() -> bookingService.updateBooking(booking.getId(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusOf(error)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("quá khứ");

        Booking unchanged = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(unchanged.getCheckInDate()).isEqualTo(OLD_CHECK_IN);
        assertThat(unchanged.getCheckOutDate()).isEqualTo(OLD_CHECK_OUT);
    }

    @Test
    void doiSangHomNay_duocPhep() {
        LocalDate today = PublicBookingDatePolicy.today();

        BookingResponse updated = bookingService.updateBooking(booking.getId(), change(today, today.plusDays(1)));

        assertThat(updated.checkInDate()).isEqualTo(today);
    }
}