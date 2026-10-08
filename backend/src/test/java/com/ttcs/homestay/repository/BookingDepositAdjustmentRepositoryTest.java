package com.ttcs.homestay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingDepositAdjustment;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.BookingSource;
import com.ttcs.homestay.entity.DepositAdjustmentType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BookingDepositAdjustmentRepositoryTest {

    @Autowired
    private BookingDepositAdjustmentRepository adjustmentRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EntityManager entityManager;

    private Booking booking;

    @BeforeEach
    void setUp() {
        booking = new Booking();
        booking.setBookingCode("BK-ADJ-TEST");
        booking.setGuestName("Adjustment test");
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setSource(BookingSource.TAI_QUAY);
        booking.setRoomTypeNameSnapshot("Standard");
        booking.setCheckInDate(LocalDate.of(2027, 1, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 1, 11));
        booking.setWeekdayPriceSnapshot(100_000);
        booking.setWeekendPriceSnapshot(100_000);
        booking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        booking.setTotalAmount(100_000);
        booking.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        booking.setExtraGuestCount(0);
        booking.setExtraPersonFeeSnapshot(0);
        booking.setSurchargeAmount(0);
        booking = bookingRepository.saveAndFlush(booking);
    }

    @Test
    void savesExactDecimalAndReturnsEntriesInChronologicalOrder() {
        BookingDepositAdjustment later = adjustment(DepositAdjustmentType.GIAM,
                "0.1001", "Refund correction", OffsetDateTime.parse("2027-01-02T10:00:00Z"));
        BookingDepositAdjustment earlier = adjustment(DepositAdjustmentType.TANG,
                "0.0001", "Additional payment", OffsetDateTime.parse("2027-01-01T10:00:00Z"));
        adjustmentRepository.save(later);
        adjustmentRepository.save(earlier);
        entityManager.flush();
        entityManager.clear();

        var saved = adjustmentRepository.findAllByBookingIdOrderByCreatedAtAscIdAsc(booking.getId());

        assertThat(saved).extracting(BookingDepositAdjustment::getAdjustmentType)
                .containsExactly(DepositAdjustmentType.TANG, DepositAdjustmentType.GIAM);
        assertThat(saved).extracting(BookingDepositAdjustment::getAmount)
                .containsExactly(new BigDecimal("0.0001"), new BigDecimal("0.1001"));
        assertThat(saved).extracting(BookingDepositAdjustment::getCreatedBy)
                .containsExactly("Actor", "Actor");
    }

    @Test
    void entityIsImmutableAndTimestampIsUtc() throws Exception {
        BookingDepositAdjustment adjustment = adjustment(DepositAdjustmentType.TANG,
                "10.2500", "Reason", OffsetDateTime.parse("2027-01-01T10:00:00+07:00"));
        BookingDepositAdjustment saved = adjustmentRepository.saveAndFlush(adjustment);
        OffsetDateTime expectedUtc = OffsetDateTime.parse("2027-01-01T03:00:00Z");
        entityManager.clear();

        BookingDepositAdjustment loaded = entityManager.find(BookingDepositAdjustment.class, saved.getId());
        loaded.setAmount(new BigDecimal("999.0000"));
        entityManager.flush();
        entityManager.clear();

        BookingDepositAdjustment unchanged = entityManager.find(BookingDepositAdjustment.class, saved.getId());
        assertThat(unchanged.getAmount()).isEqualByComparingTo("10.2500");
        assertThat(unchanged.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(unchanged.getCreatedAt()).isEqualTo(expectedUtc);
        assertThat(BookingDepositAdjustment.class.getAnnotation(org.hibernate.annotations.Immutable.class)).isNotNull();
        assertThat(BookingDepositAdjustment.class.getDeclaredField("createdAt")
                .getAnnotation(jakarta.persistence.Column.class).updatable()).isFalse();
    }

    private BookingDepositAdjustment adjustment(
            DepositAdjustmentType type, String amount, String reason, OffsetDateTime createdAt) {
        BookingDepositAdjustment adjustment = new BookingDepositAdjustment();
        adjustment.setBooking(booking);
        adjustment.setAdjustmentType(type);
        adjustment.setAmount(new BigDecimal(amount));
        adjustment.setReason(reason);
        adjustment.setCreatedBy("Actor");
        adjustment.setCreatedAt(createdAt);
        return adjustment;
    }
}
