package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.CheckoutPaymentRequest;
import com.ttcs.homestay.dto.CheckoutPreviewResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingNightlyPriceSnapshot;
import com.ttcs.homestay.entity.BookingSettlement;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.BookingSurcharge;
import com.ttcs.homestay.entity.SurchargeType;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.BookingSettlementRepository;
import com.ttcs.homestay.repository.BookingSurchargeRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutServiceTest {
    @Mock RoomRepository roomRepository;
    @Mock BookingRepository bookingRepository;
    @Mock BookingSettlementRepository settlementRepository;
    @Mock BookingSurchargeRepository surchargeRepository;
    @Mock RoomStatusHistoryRepository historyRepository;
    @Mock BookingDepositService depositService;
    @Mock OperatingSettingsRepository settingsRepository;

    private CheckoutService service;
    private OperatingSettings settings;
    private Room room;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new CheckoutService(roomRepository, bookingRepository, settlementRepository,
                surchargeRepository, historyRepository, depositService, settingsRepository,
                Clock.fixed(OffsetDateTime.parse("2026-10-12T12:00:00+07:00").toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")));
        settings = new OperatingSettings();
        settings.setId(7L);
        settings.setCheckOutTime(LocalTime.NOON);
        settings.setLateCheckoutFeePerHour(100_000L);
        settings.setCreatedAt(OffsetDateTime.parse("2026-10-01T00:00:00+07:00"));
        when(settingsRepository.findFirstByCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(any()))
                .thenReturn(Optional.of(settings));
        room = new Room();
        room.setId(12L);
        room.setRoomNumber("201");
        room.setStatus(RoomStatus.DANG_O);
        booking = new Booking();
        booking.setId(44L);
        booking.setBookingCode("BK-44");
        booking.setGuestName("Khách test");
        booking.setRoom(room);
        booking.setStatus(BookingStatus.DA_NHAN_PHONG);
        booking.setCheckInDate(LocalDate.of(2026, 10, 10));
        booking.setCheckOutDate(LocalDate.of(2026, 10, 12));
        booking.setTotalAmount(1_000_000);
        when(roomRepository.findById(12L)).thenReturn(Optional.of(room));
        when(roomRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(room));
        when(bookingRepository.findByRoomAndStatus(12L, BookingStatus.DA_NHAN_PHONG)).thenReturn(List.of(booking));
        when(bookingRepository.findByRoomAndStatusForUpdate(12L, BookingStatus.DA_NHAN_PHONG)).thenReturn(List.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.empty());
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.ZERO);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(settlementRepository.save(any(BookingSettlement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void hourlyFeeBoundariesUseCeilingAndStableTokenWithinSameBand() {
        String[] times = {"11:59", "12:00", "12:01", "13:00", "13:01", "13:20", "18:00"};
        int[] expectedHours = {0, 0, 1, 1, 2, 2, 6};
        long[] expectedFees = {0, 0, 100_000, 100_000, 200_000, 200_000, 600_000};
        CheckoutPreviewResponse[] previews = new CheckoutPreviewResponse[times.length];
        for (int i = 0; i < times.length; i++) {
            useClockAt(times[i]);
            previews[i] = service.preview(12L);
            assertThat(previews[i].lateCheckoutHours()).isEqualTo(expectedHours[i]);
            assertThat(previews[i].lateCheckoutFee()).isEqualTo(BigDecimal.valueOf(expectedFees[i]));
            assertThat(previews[i].checkoutAllowed()).isTrue();
        }
        assertThat(previews[4].previewToken()).isEqualTo(previews[5].previewToken());
        useClockAt("18:01");
        assertThat(service.preview(12L).lateCheckoutPricingBasis()).isEqualTo("LAST_NIGHT");
    }

    @Test
    void earlyCheckoutPreviewShowsUnusedNightsAndRoomValueWithoutChangingSettlement() {
        useClockAt("18:01");
        booking.setCheckOutDate(LocalDate.of(2026, 10, 14));
        booking.setTotalAmount(1_000_000L);
        booking.setNightlyPriceSnapshots(List.of(
                nightly(LocalDate.of(2026, 10, 10), 200_000, "n1"),
                nightly(LocalDate.of(2026, 10, 11), 200_000, "n2"),
                nightly(LocalDate.of(2026, 10, 12), 300_000, "n3"),
                nightly(LocalDate.of(2026, 10, 13), 300_000, "n4")));
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(100_000));

        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.checkoutAllowed()).isTrue();
        assertThat(preview.earlyCheckout()).isTrue();
        assertThat(preview.bookedNights()).isEqualTo(4);
        assertThat(preview.usedNights()).isEqualTo(2);
        assertThat(preview.unusedNights()).isEqualTo(2);
        assertThat(preview.unusedRoomValue()).isEqualByComparingTo("600000");
        assertThat(preview.lateCheckoutHours()).isZero();
        assertThat(preview.lateCheckoutFee()).isZero();
        assertThat(preview.lateCheckoutPricingBasis()).isEqualTo("NONE");
        assertThat(preview.roomTotal()).isEqualByComparingTo("1000000");
        assertThat(preview.settlementTotalBeforeDeposit()).isEqualByComparingTo("1000000");
        assertThat(preview.amount()).isEqualByComparingTo("900000");
    }

    @Test
    void earlyCheckoutOnCheckInDateReportsZeroUsedNightsAndKeepsFullPrice() {
        booking.setCheckOutDate(LocalDate.of(2026, 10, 14));
        booking.setNightlyPriceSnapshots(List.of(
                nightly(LocalDate.of(2026, 10, 10), 250_000, "n1"),
                nightly(LocalDate.of(2026, 10, 11), 250_000, "n2"),
                nightly(LocalDate.of(2026, 10, 12), 250_000, "n3"),
                nightly(LocalDate.of(2026, 10, 13), 250_000, "n4")));
        useClockAt("2026-10-10", "09:00");
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.usedNights()).isZero();
        assertThat(preview.unusedNights()).isEqualTo(4);
        assertThat(preview.unusedRoomValue()).isEqualByComparingTo("1000000");
        assertThat(preview.roomTotal()).isEqualByComparingTo("1000000");
    }

    @Test
    void earlyCheckoutReasonIsRequiredTrimmedAndSavedWithSettlement() {
        booking.setCheckOutDate(LocalDate.of(2026, 10, 14));
        useClockAt("18:01");
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false, "   "), "Reception"))
                .isInstanceOf(ResponseStatusException.class);
        service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false, "  Khách cần về sớm  "), "Reception");
        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getEarlyCheckoutReason()).isEqualTo("Khách cần về sớm");
        assertThat(captor.getValue().getCheckedOutAt()).isEqualTo(OffsetDateTime.parse("2026-10-12T18:01+07:00"));
        assertThat(captor.getValue().getLateCheckoutFee()).isZero();
        assertThat(booking.getCheckOutDate()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(booking.getTotalAmount()).isEqualTo(1_000_000L);
    }

    @Test
    void earlyCheckoutReasonLongerThan500CharactersIsRejected() {
        booking.setCheckOutDate(LocalDate.of(2026, 10, 14));
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false, "x".repeat(501)), "Reception"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, never()).save(any(BookingSettlement.class));
    }

    @Test
    void earlyCheckoutRejectsMissingAndBlankReasons() {
        booking.setCheckOutDate(LocalDate.of(2026, 10, 14));
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false, null), "Reception"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false, "   "), "Reception"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, never()).save(any(BookingSettlement.class));
    }

    @Test
    void earlyCheckoutAt1801NeverAddsLateFeeAndPreservesBookingSurchargeAndDeposit() {
        useClockAt("18:01");
        booking.setCheckOutDate(LocalDate.of(2026, 10, 14));
        booking.setNightlyPriceSnapshots(List.of(
                nightly(LocalDate.of(2026, 10, 10), 400_000, "Night 1", 100_000),
                nightly(LocalDate.of(2026, 10, 11), 400_000, "Night 2", 100_000),
                nightly(LocalDate.of(2026, 10, 12), 400_000, "Night 3", 100_000),
                nightly(LocalDate.of(2026, 10, 13), 400_000, "Night 4", 100_000)));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of(surcharge(8L, 50_000L)));
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(250_000));

        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.lateCheckoutFee()).isZero();
        assertThat(preview.settlementTotalBeforeDeposit()).isEqualByComparingTo("1050000");
        assertThat(preview.amount()).isEqualByComparingTo("800000");
        service.checkout(12L, new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false, "Reason"), "Reception");

        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionStatus()).isEqualTo("CONFIRMED");
        assertThat(captor.getValue().getRoomTotal()).isEqualByComparingTo("1000000");
        assertThat(captor.getValue().getAdditionalSurchargeTotal()).isEqualTo(50_000L);
        assertThat(captor.getValue().getDepositTotal()).isEqualByComparingTo("250000");
        assertThat(captor.getValue().getLateCheckoutFee()).isZero();
        assertThat(booking.getTotalAmount()).isEqualTo(1_000_000L);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_TRA_PHONG);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_BAN);
    }

    @Test
    void checkoutOnExpectedDateDoesNotRequireOrStoreEarlyReason() {
        CheckoutPreviewResponse preview = service.preview(12L);
        service.checkout(12L, new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Reception");
        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getEarlyCheckoutReason()).isNull();
        assertThat(preview.earlyCheckout()).isFalse();
    }

    @Test
    void earlyCheckoutStillRejectsStalePreviewAndRepeatedConfirmation() {
        booking.setCheckOutDate(LocalDate.of(2026, 10, 14));
        CheckoutPreviewResponse stale = service.preview(12L);
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L))
                .thenReturn(List.of(surcharge(903L, 75_000L)));
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(stale.previewToken(), "CASH", null, true, false, "reason"), "Reception"))
                .isInstanceOf(ResponseStatusException.class);

        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of());
        CheckoutPreviewResponse current = service.preview(12L);
        service.checkout(12L, new CheckoutPaymentRequest(current.previewToken(), "CASH", null, true, false, "reason"), "Reception");
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(new BookingSettlement()));
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(current.previewToken(), "CASH", null, true, false, "reason"), "Reception"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, org.mockito.Mockito.times(1)).save(any(BookingSettlement.class));
    }

    @Test
    void afterSixUsesLastNightRoomPriceOnlyAndBlocksUnreliableSnapshot() {
        useClockAt("18:01");
        booking.setNightlyPriceSnapshots(List.of(
                nightly(LocalDate.of(2026, 10, 10), 300_000, "night one", 100_000),
                nightly(LocalDate.of(2026, 10, 11), 500_000, "last night", 100_000)));
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(100_000);
        booking.setSurchargeAmount(200_000);
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.lateCheckoutFee()).isEqualTo(BigDecimal.valueOf(500_000));
        assertThat(preview.settlementTotalBeforeDeposit()).isEqualTo(BigDecimal.valueOf(1_500_000));

        booking.setNightlyPriceSnapshots(List.of());
        CheckoutPreviewResponse blocked = service.preview(12L);
        assertThat(blocked.checkoutAllowed()).isFalse();
        assertThat(blocked.checkoutBlockedReason()).contains("snapshot");
    }

    @Test
    void checkoutAt1801UsesLastNightSnapshotAndPersistsSettlementOnce() {
        useClockAt("18:01");
        booking.setTotalAmount(800_000L);
        booking.setNightlyPriceSnapshots(List.of(
                nightly(LocalDate.of(2026, 10, 10), 300_000, "night one"),
                nightly(LocalDate.of(2026, 10, 11), 500_000, "last night")));
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(250_000));

        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.checkoutTimeSnapshot()).isEqualTo(LocalTime.NOON);
        assertThat(preview.lateCheckoutPricingBasis()).isEqualTo("LAST_NIGHT");
        assertThat(preview.lateCheckoutHours()).isZero();
        assertThat(preview.lateCheckoutFee()).isEqualByComparingTo("500000");
        assertThat(preview.lateCheckoutFee()).isEqualTo(BigDecimal.valueOf(
                booking.getNightlyPriceSnapshots().get(1).getRoomPrice()));
        assertThat(preview.checkoutAllowed()).isTrue();
        assertThat(preview.nightlyBreakdownReliable()).isTrue();
        assertThat(preview.roomTotal()).isEqualByComparingTo("800000");
        assertThat(preview.bookingSurchargeTotal()).isEqualByComparingTo("0");
        assertThat(preview.settlementTotalBeforeDeposit()).isEqualByComparingTo("1300000");
        assertThat(preview.depositTotal()).isEqualByComparingTo("250000");
        assertThat(preview.amount()).isEqualByComparingTo("1050000");
        assertThat(preview.settlementTotalBeforeDeposit()).isEqualByComparingTo(
                preview.roomTotal().add(preview.bookingSurchargeTotal()).add(BigDecimal.valueOf(500_000)));
        assertThat(preview.amount()).isEqualByComparingTo(
                preview.settlementTotalBeforeDeposit().subtract(preview.depositTotal()));

        service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Reception");

        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        BookingSettlement saved = captor.getValue();
        assertThat(saved.getLateCheckoutFee()).isEqualTo(500_000L);
        assertThat(saved.getLateCheckoutHours()).isZero();
        assertThat(saved.getLateCheckoutPricingBasis()).isEqualTo("LAST_NIGHT");
        assertThat(saved.getFinalTotal()).isEqualTo(1_300_000L);
        assertThat(saved.getDepositTotal()).isEqualByComparingTo("250000");
        assertThat(saved.getAmount()).isEqualByComparingTo("1050000");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_TRA_PHONG);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_BAN);
        verify(settlementRepository, org.mockito.Mockito.times(1)).save(any(BookingSettlement.class));
    }

    @Test
    void hourlyFeeStartsAtConfiguredCheckoutTime() {
        settings.setCheckOutTime(LocalTime.of(11, 0));
        useClockAt("12:00");

        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.checkoutTimeSnapshot()).isEqualTo(LocalTime.of(11, 0));
        assertThat(preview.lateCheckoutHours()).isEqualTo(1);
        assertThat(preview.lateCheckoutFee()).isEqualByComparingTo("100000");
    }

    @Test
    void s402SurchargesAreAddedOnceToSettlementAndRemainSeparateFromBookingTotal() {
        BookingSurcharge line = surcharge(901L, 150_000L);
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(100_000L);
        booking.setSurchargeAmount(200_000L);
        booking.setNightlyPriceSnapshots(List.of(
                nightly(LocalDate.of(2026, 10, 10), 400_000, "Night 1", 100_000),
                nightly(LocalDate.of(2026, 10, 11), 400_000, "Night 2", 100_000)));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of(line));
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(50_000));

        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.bookingSurcharges()).hasSize(1);
        assertThat(preview.surchargeBreakdownComplete()).isTrue();
        assertThat(preview.surchargeAmount()).isEqualByComparingTo("200000");
        assertThat(preview.bookingSurchargeTotal()).isEqualByComparingTo("150000");
        assertThat(preview.settlementTotalBeforeDeposit()).isEqualByComparingTo("1150000");
        assertThat(preview.amount()).isEqualByComparingTo("1100000");

        service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Reception");
        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getRoomTotal()).isEqualByComparingTo("1000000");
        assertThat(captor.getValue().getAdditionalSurchargeTotal()).isEqualTo(150_000L);
        assertThat(captor.getValue().getLateCheckoutFee()).isEqualTo(0L);
        assertThat(captor.getValue().getFinalTotal()).isEqualTo(1_150_000L);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("1100000");
    }

    @Test
    void multipleS402LinesAreSummedOnceAndSnapshottedWithLateFee() {
        useClockAt("13:01");
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of(
                surcharge(903L, 75_000L), surcharge(904L, 125_000L)));
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(200_000));

        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.bookingSurchargeTotal()).isEqualByComparingTo("200000");
        assertThat(preview.settlementTotalBeforeDeposit()).isEqualByComparingTo("1400000");
        assertThat(preview.amount()).isEqualByComparingTo("1200000");
        service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Reception");

        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getAdditionalSurchargeTotal()).isEqualTo(200_000L);
        assertThat(captor.getValue().getLateCheckoutFee()).isEqualTo(200_000L);
        assertThat(captor.getValue().getFinalTotal()).isEqualTo(1_400_000L);
        assertThat(captor.getValue().getDepositTotal()).isEqualByComparingTo("200000");
    }

    @Test
    void checkoutAt1320StoresTwoHourlyLateFeeAndSettlesCorrectAmount() {
        useClockAt("13:20");
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of(
                surcharge(905L, 75_000L), surcharge(906L, 125_000L)));
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(200_000));

        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.checkoutTimeSnapshot()).isEqualTo(LocalTime.NOON);
        assertThat(preview.lateCheckoutHours()).isEqualTo(2);
        assertThat(preview.lateCheckoutFee()).isEqualByComparingTo("200000");
        assertThat(preview.lateCheckoutPricingBasis()).isEqualTo("HOURLY");
        assertThat(preview.checkoutAllowed()).isTrue();
        assertThat(preview.roomTotal()).isEqualByComparingTo("1000000");
        assertThat(preview.bookingSurchargeTotal()).isEqualByComparingTo("200000");
        assertThat(preview.settlementTotalBeforeDeposit()).isEqualByComparingTo(
                preview.roomTotal().add(preview.bookingSurchargeTotal()).add(BigDecimal.valueOf(200_000)));
        assertThat(preview.depositTotal()).isEqualByComparingTo("200000");
        assertThat(preview.amount()).isEqualByComparingTo(
                preview.settlementTotalBeforeDeposit().subtract(preview.depositTotal()));

        service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Reception");

        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        BookingSettlement saved = captor.getValue();
        assertThat(saved.getLateCheckoutFee()).isEqualTo(200_000L);
        assertThat(saved.getLateCheckoutHours()).isEqualTo(2);
        assertThat(saved.getLateCheckoutPricingBasis()).isEqualTo("HOURLY");
        assertThat(saved.getFinalTotal()).isEqualTo(1_400_000L);
        assertThat(saved.getDepositTotal()).isEqualByComparingTo("200000");
        assertThat(saved.getAmount()).isEqualByComparingTo("1200000");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_TRA_PHONG);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_BAN);
        verify(settlementRepository).findByBookingId(44L);
        verify(settlementRepository, org.mockito.Mockito.times(1)).save(any(BookingSettlement.class));
    }

    @Test
    void noS402LinesSnapshotsZeroAndLegacyFinalTotalRemainsNullable() {
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.bookingSurchargeTotal()).isEqualByComparingTo("0");
        assertThat(preview.settlementTotalBeforeDeposit()).isEqualByComparingTo("1000000");
        service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Reception");

        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getAdditionalSurchargeTotal()).isZero();
        assertThat(captor.getValue().getFinalTotal()).isEqualTo(1_000_000L);

        BookingSettlement legacy = new BookingSettlement();
        assertThat(legacy.getFinalTotal()).isNull();
    }

    @Test
    void addingS402SurchargeInvalidatesExistingPreviewToken() {
        CheckoutPreviewResponse preview = service.preview(12L);
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L))
                .thenReturn(List.of(surcharge(902L, 75_000L)));

        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Reception"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, never()).save(any(BookingSettlement.class));
    }

    @Test
    void changedSettingsVersionInvalidatesToken() {
        useClockAt("13:20");
        CheckoutPreviewResponse preview = service.preview(12L);
        settings.setId(8L);
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Reception"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, never()).save(any(BookingSettlement.class));
    }

    @Test
    void earlyCheckoutCanBePreviewedButRequiresReasonAndCheckoutAfterExpectedDateIsBlocked() {
        useClockAt("2026-10-11", "13:00");
        CheckoutPreviewResponse earlyPreview = service.preview(12L);
        assertThat(earlyPreview.checkoutAllowed()).isTrue();
        assertThat(earlyPreview.earlyCheckout()).isTrue();
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(earlyPreview.previewToken(), "CASH", null, true, false), "Reception"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, never()).save(any(BookingSettlement.class));

        useClockAt("2026-10-13", "13:00");
        assertThat(service.preview(12L).checkoutAllowed()).isFalse();

        useClockAt("2026-10-12", "13:00");
        CheckoutPreviewResponse expectedDayPreview = service.preview(12L);
        assertThat(expectedDayPreview.checkoutAllowed()).isTrue();
        assertThat(expectedDayPreview.earlyCheckout()).isFalse();
        service.checkout(12L,
                new CheckoutPaymentRequest(expectedDayPreview.previewToken(), "CASH", null, true, false), "Reception");
        verify(settlementRepository).save(any(BookingSettlement.class));
    }

    private void useClockAt(String time) {
        useClockAt("2026-10-12", time);
    }

    private void useClockAt(String date, String time) {
        Clock fixed = Clock.fixed(OffsetDateTime.parse(date + "T" + time + ":00+07:00").toInstant(),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new CheckoutService(roomRepository, bookingRepository, settlementRepository,
                surchargeRepository, historyRepository, depositService, settingsRepository, fixed);
    }

    private BookingSurcharge surcharge(long id, long amount) {
        BookingSurcharge line = new BookingSurcharge();
        line.setId(id);
        line.setBooking(booking);
        line.setSurchargeType(SurchargeType.THEM_GIUONG);
        line.setQuantity(1);
        line.setUnitPrice(amount);
        line.setNights(1);
        line.setAmount(amount);
        return line;
    }

    @Test
    void noDepositCollectsFullAmount() {
        CheckoutPreviewResponse preview = service.preview(12L);
        service.checkout(12L, new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Lễ tân");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_TRA_PHONG);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_BAN);
        verify(settlementRepository).save(any(BookingSettlement.class));
    }

    @Test
    void surchargeIsInformationalAndNotAddedTwiceToSettlement() {
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(100_000L);
        booking.setSurchargeAmount(200_000L);
        booking.setNightlyPriceSnapshots(List.of(
                nightly(LocalDate.of(2026, 10, 10), 400_000, "Đêm 1", 100_000),
                nightly(LocalDate.of(2026, 10, 11), 400_000, "Đêm 2", 100_000)));
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.roomTotal()).isEqualByComparingTo("1000000");
        assertThat(preview.surchargeAmount()).isEqualByComparingTo("200000");
        assertThat(preview.surchargeBreakdownComplete()).isTrue();
        assertThat(preview.extraGuestCount()).isEqualTo(1);
        assertThat(preview.extraPersonFeeSnapshot()).isEqualTo(100_000L);
        assertThat(preview.surchargeNights()).isEqualTo(2L);
        assertThat(preview.roomTotalExcludingSurcharge()).isEqualByComparingTo("800000");
        assertThat(preview.amount()).isEqualByComparingTo("1000000");

        service.checkout(12L, new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Lá»… tÃ¢n");
        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getRoomTotal()).isEqualByComparingTo("1000000");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    void legacyBookingWithoutSnapshotsKeepsCommittedTotalAndSavedSurcharge() {
        booking.setSurchargeAmount(200_000L);
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.nightlyBreakdownReliable()).isFalse();
        assertThat(preview.surchargeAmount()).isEqualByComparingTo("200000");
        assertThat(preview.surchargeBreakdownComplete()).isFalse();
        assertThat(preview.extraGuestCount()).isNull();
        assertThat(preview.extraPersonFeeSnapshot()).isNull();
        assertThat(preview.surchargeNights()).isNull();
        assertThat(preview.roomTotalExcludingSurcharge()).isNull();
        assertThat(preview.surchargeBreakdownWarning()).isNotBlank();
        assertThat(preview.roomTotal()).isEqualByComparingTo("1000000");
        assertThat(preview.amount()).isEqualByComparingTo("1000000");
    }

    @Test
    void legacyBreakdownThatDoesNotMatchStoredTotalIsHidden() {
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(100_000L);
        booking.setSurchargeAmount(200_000L);
        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.surchargeBreakdownComplete()).isFalse();
        assertThat(preview.surchargeAmount()).isEqualByComparingTo("200000");
        assertThat(preview.roomTotalExcludingSurcharge()).isNull();
        assertThat(preview.surchargeBreakdownWarning()).isNotBlank();
    }

    @Test
    void changedSurchargeInvalidatesPreviousPreviewToken() {
        assertTokenInvalidatedByBookingMutation(() -> booking.setSurchargeAmount(1L));
    }

    @Test
    void changedExtraGuestCountInvalidatesPreviousPreviewToken() {
        assertTokenInvalidatedByBookingMutation(() -> booking.setExtraGuestCount(1));
    }

    @Test
    void changedFeeSnapshotInvalidatesPreviousPreviewToken() {
        assertTokenInvalidatedByBookingMutation(() -> booking.setExtraPersonFeeSnapshot(1));
    }

    private void assertTokenInvalidatedByBookingMutation(Runnable mutation) {
        CheckoutPreviewResponse preview = service.preview(12L);
        mutation.run();
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Lá»… tÃ¢n"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, never()).save(any(BookingSettlement.class));
    }

    @Test
    void nullLikeLegacySurchargeDefaultsToZeroWithoutInference() {
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.surchargeAmount()).isEqualByComparingTo("0");
        assertThat(preview.surchargeBreakdownComplete()).isTrue();
        assertThat(preview.extraGuestCount()).isEqualTo(0);
        assertThat(preview.extraPersonFeeSnapshot()).isEqualTo(0L);
        assertThat(preview.surchargeNights()).isEqualTo(2L);
        assertThat(preview.roomTotalExcludingSurcharge()).isEqualByComparingTo("1000000");
    }

    @Test
    void oneExtraGuestForOneNightHasCompleteBreakdown() {
        booking.setCheckOutDate(LocalDate.of(2026, 10, 11));
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(200_000L);
        booking.setSurchargeAmount(200_000L);
        booking.setTotalAmount(700_000L);
        booking.setNightlyPriceSnapshots(List.of(nightly(LocalDate.of(2026, 10, 10), 500_000, "Đêm 1", 200_000)));

        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.surchargeBreakdownComplete()).isTrue();
        assertThat(preview.extraGuestCount()).isEqualTo(1);
        assertThat(preview.surchargeNights()).isEqualTo(1L);
        assertThat(preview.roomTotalExcludingSurcharge()).isEqualByComparingTo("500000");
    }

    @Test
    void multipleExtraGuestsUseSavedFeeSnapshot() {
        booking.setExtraGuestCount(2);
        booking.setExtraPersonFeeSnapshot(100_000L);
        booking.setSurchargeAmount(400_000L);
        booking.setNightlyPriceSnapshots(List.of(
                nightly(LocalDate.of(2026, 10, 10), 300_000, "Đêm 1", 200_000),
                nightly(LocalDate.of(2026, 10, 11), 300_000, "Đêm 2", 200_000)));

        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.surchargeBreakdownComplete()).isTrue();
        assertThat(preview.extraGuestCount()).isEqualTo(2);
        assertThat(preview.extraPersonFeeSnapshot()).isEqualTo(100_000L);
        assertThat(preview.surchargeNights()).isEqualTo(2L);
    }

    @Test
    void surchargeMismatchDoesNotExposeInferredBreakdown() {
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(200_000L);
        booking.setSurchargeAmount(300_000L);
        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.surchargeBreakdownComplete()).isFalse();
        assertThat(preview.extraGuestCount()).isNull();
        assertThat(preview.extraPersonFeeSnapshot()).isNull();
        assertThat(preview.surchargeNights()).isNull();
        assertThat(preview.roomTotalExcludingSurcharge()).isNull();
        assertThat(preview.surchargeAmount()).isEqualByComparingTo("300000");
    }

    @Test
    void nightlySurchargeMismatchDoesNotExposeBreakdown() {
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(100_000L);
        booking.setSurchargeAmount(200_000L);
        booking.setNightlyPriceSnapshots(List.of(
                nightly(LocalDate.of(2026, 10, 10), 400_000, "Đêm 1", 100_000),
                nightly(LocalDate.of(2026, 10, 11), 400_000, "Đêm 2", 0)));

        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.surchargeBreakdownComplete()).isFalse();
        assertThat(preview.roomTotalExcludingSurcharge()).isNull();
    }

    @Test
    void depositInsufficientCollectsDifferenceAndRequiresReferenceForTransfer() {
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(250_000));
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.amount()).isEqualByComparingTo("750000");
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "BANK_TRANSFER", " ", true, false), "Lễ tân"))
                .isInstanceOf(ResponseStatusException.class);
        service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "BANK_TRANSFER", "TX-1", true, false), "Lễ tân");
        verify(settlementRepository).save(any(BookingSettlement.class));
    }

    @Test
    void checkoutPersistsCurrentDepositTotalIncludingAdjustments() {
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(700_000));
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.depositTotal()).isEqualByComparingTo("700000");

        service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Lễ tân");

        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getDepositTotal()).isEqualByComparingTo("700000");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("300000");
    }

    @Test
    void exactDepositCreatesZeroSettlementWithoutPaymentConfirmation() {
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(1_000_000));
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.settlementType()).isEqualTo("ZERO");
        service.checkout(12L, new CheckoutPaymentRequest(preview.previewToken(), null, null, null, null), "Lễ tân");
        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionStatus()).isEqualTo("NOT_APPLICABLE");
    }

    @Test
    void previewUsesCommittedNightSnapshotsAndDoesNotLookUpCurrentPrices() {
        BookingNightlyPriceSnapshot first = nightly(LocalDate.of(2026, 10, 10), 400_000, "Giá chốt đêm 1");
        BookingNightlyPriceSnapshot second = nightly(LocalDate.of(2026, 10, 11), 600_000, "Giá chốt đêm 2");
        booking.setNightlyPriceSnapshots(List.of(first, second));
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.nightlyBreakdownReliable()).isTrue();
        assertThat(preview.nightlyPrices()).extracting(line -> line.roomPrice())
                .containsExactly(400_000L, 600_000L);
        assertThat(preview.warning()).isNull();
    }

    @Test
    void excessDepositRequiresActualRefundConfirmation() {
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(1_200_000));
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.amount()).isEqualByComparingTo("200000");
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, false, false), "Lễ tân"))
                .isInstanceOf(ResponseStatusException.class);
        service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, false, true), "Lễ tân");
        verify(settlementRepository).save(any(BookingSettlement.class));
    }

    @Test
    void changedPreviewDataRejectsConfirmation() {
        CheckoutPreviewResponse preview = service.preview(12L);
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(5));
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Lễ tân"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, never()).save(any(BookingSettlement.class));
    }

    @Test
    void secondCheckoutIsRejectedByRoomState() {
        room.setStatus(RoomStatus.TRONG_BAN);
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest("token", "CASH", null, true, false), "Lễ tân"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, never()).save(any(BookingSettlement.class));
    }

    @Test
    void previewAndCheckoutRejectRoomNotOccupied() {
        room.setStatus(RoomStatus.TRONG_SACH);
        assertThatThrownBy(() -> service.preview(12L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest("token", "CASH", null, true, false), "Lễ tân"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, never()).save(any(BookingSettlement.class));
    }

    @Test
    void previewAndCheckoutRejectMissingOrAmbiguousInStayBooking() {
        when(bookingRepository.findByRoomAndStatus(12L, BookingStatus.DA_NHAN_PHONG)).thenReturn(List.of());
        when(bookingRepository.findByRoomAndStatusForUpdate(12L, BookingStatus.DA_NHAN_PHONG)).thenReturn(List.of());
        assertThatThrownBy(() -> service.preview(12L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest("token", "CASH", null, true, false), "Lễ tân"))
                .isInstanceOf(ResponseStatusException.class);

        when(bookingRepository.findByRoomAndStatus(12L, BookingStatus.DA_NHAN_PHONG))
                .thenReturn(List.of(booking, new Booking()));
        when(bookingRepository.findByRoomAndStatusForUpdate(12L, BookingStatus.DA_NHAN_PHONG))
                .thenReturn(List.of(booking, new Booking()));
        assertThatThrownBy(() -> service.preview(12L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest("token", "CASH", null, true, false), "Lễ tân"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, never()).save(any(BookingSettlement.class));
    }

    @Test
    void previewAndCheckoutRejectBookingAttachedToDifferentRoom() {
        Room otherRoom = new Room();
        otherRoom.setId(99L);
        booking.setRoom(otherRoom);

        assertThatThrownBy(() -> service.preview(12L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest("token", "CASH", null, true, false), "Lễ tân"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository, never()).save(any(BookingSettlement.class));
    }

    @Test
    void successfulCheckoutCannotBeRepeated() {
        CheckoutPreviewResponse preview = service.preview(12L);
        service.checkout(12L, new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Lễ tân");
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Lễ tân"))
                .isInstanceOf(ResponseStatusException.class);
        verify(settlementRepository).save(any(BookingSettlement.class));
    }

    @Test
    void missingOrAmbiguousInStayBookingIsRejected() {
        when(bookingRepository.findByRoomAndStatus(12L, BookingStatus.DA_NHAN_PHONG)).thenReturn(List.of());
        assertThatThrownBy(() -> service.preview(12L)).isInstanceOf(ResponseStatusException.class);
        when(bookingRepository.findByRoomAndStatus(12L, BookingStatus.DA_NHAN_PHONG)).thenReturn(List.of(booking, new Booking()));
        assertThatThrownBy(() -> service.preview(12L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void failedSettlementWriteDoesNotChangeBookingOrRoom() {
        CheckoutPreviewResponse preview = service.preview(12L);
        when(settlementRepository.save(any(BookingSettlement.class))).thenThrow(new RuntimeException("write failed"));
        assertThatThrownBy(() -> service.checkout(12L,
                new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Lễ tân"))
                .isInstanceOf(RuntimeException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_NHAN_PHONG);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.DANG_O);
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    void surchargeCheckoutSubtractsDepositFromCommittedBookingTotalExactly() {
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(100_000L);
        booking.setSurchargeAmount(200_000L);
        booking.setNightlyPriceSnapshots(List.of(
                nightly(LocalDate.of(2026, 10, 10), 400_000, "Đêm 1", 100_000),
                nightly(LocalDate.of(2026, 10, 11), 400_000, "Đêm 2", 100_000)));
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(250_000));

        CheckoutPreviewResponse preview = service.preview(12L);

        assertThat(preview.roomTotal()).isEqualByComparingTo("1000000");
        assertThat(preview.amount()).isEqualByComparingTo("750000");
        assertThat(preview.surchargeBreakdownComplete()).isTrue();
    }

    @Test
    void surchargeCheckoutSucceedsWithoutAddingSurchargeAgain() {
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(100_000L);
        booking.setSurchargeAmount(200_000L);
        booking.setNightlyPriceSnapshots(List.of(
                nightly(LocalDate.of(2026, 10, 10), 400_000, "Đêm 1", 100_000),
                nightly(LocalDate.of(2026, 10, 11), 400_000, "Đêm 2", 100_000)));
        when(depositService.currentDepositTotalExact(booking)).thenReturn(BigDecimal.valueOf(250_000));
        CheckoutPreviewResponse preview = service.preview(12L);

        service.checkout(12L, new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Lễ tân");

        org.mockito.ArgumentCaptor<BookingSettlement> captor =
                org.mockito.ArgumentCaptor.forClass(BookingSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getRoomTotal()).isEqualByComparingTo("1000000");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("750000");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_TRA_PHONG);
    }

    private BookingNightlyPriceSnapshot nightly(LocalDate date, long price, String label) {
        return nightly(date, price, label, 0);
    }

    private BookingNightlyPriceSnapshot nightly(LocalDate date, long price, String label, long surcharge) {
        BookingNightlyPriceSnapshot line = new BookingNightlyPriceSnapshot();
        line.setBooking(booking);
        line.setNightDate(date);
        line.setPriceType("OVERRIDE");
        line.setPriceLabel(label);
        line.setRoomPrice(price);
        line.setSurchargeAmount(surcharge);
        return line;
    }
}
