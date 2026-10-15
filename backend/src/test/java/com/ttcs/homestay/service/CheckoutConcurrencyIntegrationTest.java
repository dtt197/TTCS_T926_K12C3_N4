package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ttcs.homestay.dto.CheckoutPaymentRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingNightlyPriceSnapshot;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.BookingSettlementRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    @Autowired private CheckoutService checkoutService;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingRepository bookings;
    @Autowired private BookingSettlementRepository settlements;
    @Autowired private RoomStatusHistoryRepository histories;

    private Long roomId;

    @BeforeEach
    void setUp() {
        settlements.deleteAll();
        histories.deleteAll();
        bookings.deleteAll();
        rooms.deleteAll();

        Room room = new Room();
        room.setRoomNumber("CHECKOUT-RACE");
        room.setFloor(1);
        room.setRoomType("Test");
        room.setStatus(RoomStatus.DANG_O);
        roomId = rooms.saveAndFlush(room).getId();

        Booking booking = new Booking();
        booking.setRoom(rooms.findById(roomId).orElseThrow());
        booking.setRoomTypeNameSnapshot("Test");
        booking.setCheckInDate(LocalDate.of(2026, 10, 14));
        booking.setCheckOutDate(LocalDate.of(2026, 10, 15));
        booking.setWeekdayPriceSnapshot(100_000);
        booking.setWeekendPriceSnapshot(100_000);
        booking.setWeekendDaysSnapshot("SATURDAY,SUNDAY");
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(50_000);
        booking.setSurchargeAmount(50_000);
        booking.setTotalAmount(150_000);
        BookingNightlyPriceSnapshot nightly = new BookingNightlyPriceSnapshot();
        nightly.setBooking(booking);
        nightly.setNightDate(LocalDate.of(2026, 10, 14));
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
    }

    @Test
    void simultaneousCheckoutCreatesOneSettlementAndClosesOnce() throws Exception {
        var preview = checkoutService.preview(roomId);
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
            assertThat(first.get(15, TimeUnit.SECONDS).booleanValue()
                    ^ second.get(15, TimeUnit.SECONDS).booleanValue()).isTrue();
        } finally {
            executor.shutdownNow();
        }
        assertThat(settlements.count()).isEqualTo(1);
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
                .extracting(Booking::getStatus).isEqualTo(BookingStatus.DA_NHAN_PHONG);
        assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.DANG_O);
        assertThat(histories.findByRoomIdInChronologicalOrder(roomId)).isEmpty();
    }

    private boolean checkout(CountDownLatch start, CheckoutPaymentRequest request) throws Exception {
        start.await();
        try {
            checkoutService.checkout(roomId, request, "Receptionist");
            return true;
        } catch (ResponseStatusException expectedConflict) {
            return false;
        }
    }
}
