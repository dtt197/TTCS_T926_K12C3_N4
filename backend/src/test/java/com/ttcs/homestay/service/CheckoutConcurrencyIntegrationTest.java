package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ttcs.homestay.dto.CheckoutPaymentRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingNightlyPriceSnapshot;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.BookingSurcharge;
import com.ttcs.homestay.entity.SurchargeType;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.BookingSettlementRepository;
import com.ttcs.homestay.repository.BookingSurchargeRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:checkout_concurrency_it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class CheckoutConcurrencyIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(CheckoutConcurrencyIntegrationTest.class);
    @Autowired private CheckoutService checkoutService;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingRepository bookings;
    @Autowired private BookingSettlementRepository settlements;
    @Autowired private BookingSurchargeRepository surcharges;
    @Autowired private RoomStatusHistoryRepository histories;
    @Autowired private OperatingSettingsRepository settings;

    private Long roomId;

    @BeforeEach
    void setUp() {
        settlements.deleteAll();
        surcharges.deleteAll();
        histories.deleteAll();
        bookings.deleteAll();
        rooms.deleteAll();
        settings.deleteAll();

        OperatingSettings operatingSettings = new OperatingSettings();
        operatingSettings.setHomestayName("Test");
        operatingSettings.setCheckInTime(LocalTime.of(14, 0));
        operatingSettings.setCheckOutTime(LocalTime.NOON);
        operatingSettings.setLateCheckoutFeePerHour(100_000);
        operatingSettings.setExtraPersonFee(50_000);
        operatingSettings.setWeekendDays("SATURDAY,SUNDAY");
        operatingSettings.setCreatedByName("Test");
        operatingSettings.setCreatedAt(OffsetDateTime.now().minusDays(1));
        settings.saveAndFlush(operatingSettings);

        Room room = new Room();
        room.setRoomNumber("CHECKOUT-RACE");
        room.setFloor(1);
        room.setRoomType("Test");
        room.setStatus(RoomStatus.DANG_O);
        roomId = rooms.saveAndFlush(room).getId();

        Booking booking = new Booking();
        booking.setRoom(rooms.findById(roomId).orElseThrow());
        booking.setRoomTypeNameSnapshot("Test");
        // Cùng múi giờ với CheckoutService (Asia/Ho_Chi_Minh); máy CI chạy UTC nên LocalDate.now() lệch ngày từ 0h–7h sáng giờ VN.
        LocalDate checkOutDate = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate checkInDate = checkOutDate.minusDays(1);
        booking.setCheckInDate(checkInDate);
        booking.setCheckOutDate(checkOutDate);
        booking.setWeekdayPriceSnapshot(100_000);
        booking.setWeekendPriceSnapshot(100_000);
        booking.setWeekendDaysSnapshot("SATURDAY,SUNDAY");
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(50_000);
        booking.setSurchargeAmount(50_000);
        booking.setTotalAmount(150_000);
        BookingNightlyPriceSnapshot nightly = new BookingNightlyPriceSnapshot();
        nightly.setBooking(booking);
        nightly.setNightDate(checkInDate);
        nightly.setPriceType("WEEKDAY");
        nightly.setPriceLabel("Weekday");
        nightly.setRoomPrice(100_000);
        nightly.setSurchargeAmount(50_000);
        booking.getNightlyPriceSnapshots().add(nightly);
        booking.setCreatedAt(OffsetDateTime.now());
        booking.setBookingCode("BK-CHECKOUT-RACE");
        booking.setGuestName("Guest");
        booking.setStatus(BookingStatus.DA_NHAN_PHONG);
        bookings.saveAndFlush(booking);
        BookingSurcharge surcharge = new BookingSurcharge();
        surcharge.setBooking(booking);
        surcharge.setSurchargeType(SurchargeType.THEM_GIUONG);
        surcharge.setQuantity(1);
        surcharge.setUnitPrice(75_000);
        surcharge.setNights(1);
        surcharge.setAmount(75_000);
        surcharge.setCreatedBy("Test");
        surcharge.setCreatedAt(OffsetDateTime.now());
        surcharges.saveAndFlush(surcharge);
    }

    @Test
    void simultaneousCheckoutCreatesOneSettlementAndClosesOnce() throws Exception {
        var preview = checkoutService.preview(roomId);
        assertThat(preview.bookingSurchargeTotal()).isEqualByComparingTo("75000");
        assertThat(preview.surchargeBreakdownComplete()).isTrue();
        assertThat(preview.extraGuestCount()).isEqualTo(1);
        assertThat(preview.surchargeNights()).isEqualTo(1L);
        var request = new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> checkout(start, request));
            Future<Boolean> second = executor.submit(() -> checkout(start, request));
            start.countDown();
            boolean firstSucceeded = first.get(15, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(15, TimeUnit.SECONDS);
            log.info("Checkout race outcomes: first={}, second={}", firstSucceeded ? "SUCCESS" : "CONFLICT",
                    secondSucceeded ? "SUCCESS" : "CONFLICT");
            assertThat(firstSucceeded ^ secondSucceeded).isTrue();
        } finally {
            executor.shutdownNow();
        }
        assertThat(settlements.count()).isEqualTo(1);
        var savedSettlement = settlements.findByBookingId(bookings.findAll().get(0).getId()).orElseThrow();
        assertThat(savedSettlement.getAmount()).isEqualByComparingTo(preview.amount());
        assertThat(savedSettlement.getRoomTotal()).isEqualByComparingTo(preview.roomTotal());
        assertThat(savedSettlement.getAdditionalSurchargeTotal())
                .isEqualTo(preview.bookingSurchargeTotal().longValueExact());
        assertThat(savedSettlement.getLateCheckoutFee()).isEqualTo(preview.lateCheckoutFee().longValueExact());
        assertThat(savedSettlement.getFinalTotal())
                .isEqualTo(preview.settlementTotalBeforeDeposit().longValueExact());
        assertThat(savedSettlement.getDepositTotal()).isEqualByComparingTo(preview.depositTotal());
        assertThat(bookings.findAll().get(0).getStatus()).isEqualTo(BookingStatus.DA_TRA_PHONG);
        assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.TRONG_BAN);
        assertThat(histories.findByRoomIdInChronologicalOrder(roomId)).hasSize(1);
    }

    @Test
    void settlementWriteFailureRollsBackBookingAndRoomChanges() {
        var preview = checkoutService.preview(roomId);
        var request = new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false);

        assertThatThrownBy(() -> checkoutService.checkout(roomId, request, "R".repeat(121)))
                .isInstanceOf(RuntimeException.class);

        assertThat(settlements.count()).isZero();
        assertThat(bookings.findAll()).singleElement()
                .extracting(booking -> booking.getStatus()).isEqualTo(BookingStatus.DA_NHAN_PHONG);
        assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.DANG_O);
        assertThat(histories.findByRoomIdInChronologicalOrder(roomId)).isEmpty();
    }

    private boolean checkout(CountDownLatch start, CheckoutPaymentRequest request) throws Exception {
        start.await();
        try {
            checkoutService.checkout(roomId, request, "Receptionist");
            return true;
        } catch (ResponseStatusException expectedConflict) {
            log.info("Checkout call rejected: HTTP status={}, reason={}",
                    expectedConflict.getStatusCode().value(), expectedConflict.getReason());
            return false;
        }
    }
}
