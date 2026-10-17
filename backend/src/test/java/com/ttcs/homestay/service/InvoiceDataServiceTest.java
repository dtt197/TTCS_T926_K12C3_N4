package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.InvoiceData;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingNightlyPriceSnapshot;
import com.ttcs.homestay.entity.BookingSettlement;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.BookingSurcharge;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.SurchargeType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.BookingSettlementRepository;
import com.ttcs.homestay.repository.BookingSurchargeRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InvoiceDataServiceTest {
    @Mock BookingRepository bookingRepository;
    @Mock BookingSettlementRepository settlementRepository;
    @Mock BookingSurchargeRepository surchargeRepository;
    @Mock OperatingSettingsRepository settingsRepository;

    private InvoiceDataService service;
    private Booking booking;
    private BookingSettlement settlement;

    @BeforeEach
    void setUp() {
        service = new InvoiceDataService(bookingRepository, settlementRepository, surchargeRepository, settingsRepository);
        Room room = new Room();
        room.setId(12L);
        room.setRoomNumber("201");
        booking = new Booking();
        booking.setId(44L);
        booking.setBookingCode("BK-44");
        booking.setGuestName("Nguyễn An");
        booking.setGuestPhone("0900000000");
        booking.setRoom(room);
        booking.setRoomTypeNameSnapshot("Phòng tiêu chuẩn");
        booking.setStatus(BookingStatus.DA_TRA_PHONG);
        booking.setCheckInDate(LocalDate.of(2026, 10, 10));
        booking.setCheckOutDate(LocalDate.of(2026, 10, 12));
        booking.setTotalAmount(1_000_000L);
        setNightlySnapshots(0, 0, 500_000);

        settlement = new BookingSettlement();
        settlement.setBooking(booking);
        settlement.setRoomId(12L);
        settlement.setRoomTotal(BigDecimal.valueOf(1_000_000));
        settlement.setDepositTotal(BigDecimal.valueOf(300_000));
        settlement.setFinalTotal(1_300_000L);
        settlement.setAdditionalSurchargeTotal(200_000L);
        settlement.setLateCheckoutFee(100_000L);
        settlement.setSettlementType(BookingSettlement.SettlementType.COLLECT);
        settlement.setAmount(BigDecimal.valueOf(1_000_000));
        settlement.setTransactionStatus("CONFIRMED");
        settlement.setOperatingSettingsId(7L);
        settlement.setCheckedOutAt(OffsetDateTime.parse("2026-10-12T13:00:00+07:00"));
    }

    @Test
    void includesAutomaticExtraPersonSnapshotWithoutAddingItAgainToSettlementTotal() {
        setNightlySnapshots(1, 100_000, 400_000);
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(100_000);
        booking.setSurchargeAmount(200_000);
        settlement.setAdditionalSurchargeTotal(0);
        settlement.setFinalTotal(1_100_000L);
        settlement.setAmount(BigDecimal.valueOf(800_000));
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of());

        InvoiceData data = service.getInvoiceData(44L);

        assertThat(data.lineItems().get(0).amount()).isEqualByComparingTo("800000");
        assertThat(data.lineItems().get(0).description()).isEqualTo("Tiền phòng cơ bản (chưa gồm phụ thu)");
        assertThat(data.lineItems().get(1).amount()).isEqualByComparingTo("200000");
        assertThat(data.lineItems().get(1).description()).contains("Phụ thu tự động đã chốt", "100000", "2 đêm");
        assertThat(data.finalTotal()).isEqualByComparingTo("1100000");
        assertThat(data.lineItems().get(3).amount()).isEqualByComparingTo("1100000");
    }

    @Test
    void combinesAutomaticAndS4ExtraPersonAmountsWithoutChangingSettlementAmounts() {
        setNightlySnapshots(1, 100_000, 400_000);
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(100_000);
        booking.setSurchargeAmount(200_000);
        settlement.setAdditionalSurchargeTotal(200_000);
        settlement.setFinalTotal(1_300_000L);
        settlement.setAmount(BigDecimal.valueOf(1_000_000));
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L))
                .thenReturn(List.of(surcharge(SurchargeType.THEM_NGUOI, 200_000)));

        InvoiceData data = service.getInvoiceData(44L);

        assertThat(data.lineItems().get(0).amount()).isEqualByComparingTo("800000");
        assertThat(data.lineItems().get(1).amount()).isEqualByComparingTo("400000");
        assertThat(data.lineItems().get(1).description()).contains("Phụ thu tự động đã chốt", "Phụ thu phát sinh");
        assertThat(data.lineItems().get(1).amount()).isEqualByComparingTo("400000");
        assertThat(data.finalTotal()).isEqualByComparingTo("1300000");
        assertThat(data.settlementAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    void keepsPlainRoomLabelAndShowsManualExtraPersonWhenAutomaticSurchargeIsZero() {
        settlement.setAdditionalSurchargeTotal(100_000);
        settlement.setFinalTotal(1_200_000L);
        settlement.setAmount(BigDecimal.valueOf(900_000));
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L))
                .thenReturn(List.of(surcharge(SurchargeType.THEM_NGUOI, 100_000)));

        InvoiceData data = service.getInvoiceData(44L);

        assertThat(data.lineItems().get(0).description()).isEqualTo("Tiền phòng");
        assertThat(data.lineItems().get(1).amount()).isEqualByComparingTo("100000");
        assertThat(data.lineItems().get(1).description()).contains("Phụ thu tự động đã chốt: 0", "Phụ thu phát sinh");
        assertThat(data.finalTotal()).isEqualByComparingTo("1200000");
        assertThat(data.settlementAmount()).isEqualByComparingTo("900000");
    }

    @Test
    void rejectsHistoricalBookingWhenNightlySnapshotIsMissing() {
        booking.getNightlyPriceSnapshots().clear();
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of(
                surcharge(SurchargeType.THEM_NGUOI, 100_000), surcharge(SurchargeType.THEM_GIUONG, 100_000)));

        assertThatThrownBy(() -> service.getInvoiceData(44L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Thiếu snapshot theo đêm");
    }

    @Test
    void rejectsInconsistentAutomaticSurchargeSnapshot() {
        setNightlySnapshots(1, 50_000, 450_000);
        booking.setExtraGuestCount(1);
        booking.setExtraPersonFeeSnapshot(100_000);
        booking.setSurchargeAmount(200_000);
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L))
                .thenReturn(List.of(surcharge(SurchargeType.THEM_NGUOI, 200_000),
                        surcharge(SurchargeType.THEM_GIUONG, 0)));

        assertThatThrownBy(() -> service.getInvoiceData(44L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("không khớp roomTotal");
    }

    private void setNightlySnapshots(long count, long fee, long roomPriceEachNight) {
        booking.setExtraGuestCount(Math.toIntExact(count));
        booking.setExtraPersonFeeSnapshot(fee);
        booking.setSurchargeAmount(count * fee * 2);
        booking.getNightlyPriceSnapshots().clear();
        for (int i = 0; i < 2; i++) {
            BookingNightlyPriceSnapshot snapshot = new BookingNightlyPriceSnapshot();
            snapshot.setNightDate(booking.getCheckInDate().plusDays(i));
            snapshot.setRoomPrice(roomPriceEachNight);
            snapshot.setSurchargeAmount(count * fee);
            booking.getNightlyPriceSnapshots().add(snapshot);
        }
    }

    @Test
    void assemblesOrderedLinesAndChecksPersistedSettlementAmounts() {
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        BookingSurcharge overridden = surcharge(SurchargeType.THEM_NGUOI, 100_000, 150_000, 200_000,
                "Price adjusted for guest");
        BookingSurcharge zeroPrice = surcharge(SurchargeType.THEM_NGUOI, 0, 150_000, 0, "Free");
        BookingSurcharge bed = surcharge(SurchargeType.THEM_GIUONG, 0, 80_000, 0, null);
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L))
                .thenReturn(List.of(overridden, zeroPrice, bed));
        InvoiceData data = service.getInvoiceData(44L);

        assertThat(data.nights()).isEqualTo(2);
        assertThat(data.homestay().address()).isEqualTo("123 Đường A");
        assertThat(data.companyName()).isNull();
        assertThat(data.companyTaxCode()).isNull();
        assertThat(data.lineItems()).extracting(lineItem -> lineItem.code()).containsExactly(
                "ROOM", "EXTRA_PERSON", "LATE_CHECKOUT",
                "TOTAL_BEFORE_DEPOSIT", "DEPOSIT", "CHECKOUT_PAYMENT", "TOTAL_PAID", "BALANCE", "PAYMENT_STATUS");
        assertThat(data.lineItems()).extracting(lineItem -> lineItem.description()).doesNotContain("Thêm giường");
        assertThat(data.lineItems().get(1).amount()).isEqualByComparingTo("200000");
        assertThat(data.lineItems().get(1).description()).contains("100000", "Price adjusted for guest", "0");
        assertThat(data.lineItems().get(1).description()).contains("Phụ thu tự động đã chốt: 0");
        assertThat(data.lineItems().get(2).amount()).isEqualByComparingTo("100000");
        assertThat(data.lineItems().get(3).amount()).isEqualByComparingTo("1300000");
        assertThat(data.lineItems().get(4).amount()).isEqualByComparingTo("-300000");
        assertThat(data.lineItems().get(5).amount()).isEqualByComparingTo("1000000");
        assertThat(data.lineItems().get(6).amount()).isEqualByComparingTo("1300000");
        assertThat(data.lineItems().get(7).amount()).isEqualByComparingTo("0");
        assertThat(data.finalTotal()).isEqualByComparingTo("1300000");
        assertThat(data.depositTotal()).isEqualByComparingTo("300000");
        assertThat(data.settlementType()).isEqualTo(BookingSettlement.SettlementType.COLLECT);
        assertThat(data.settlementAmount()).isEqualByComparingTo("1000000");
        assertThat(data.checkoutPayment()).isEqualByComparingTo("1000000");
        assertThat(data.totalPaid()).isEqualByComparingTo("1300000");
        assertThat(data.remainingAmount()).isEqualByComparingTo("0");
    }

    @Test
    void confirmedCheckoutCollectionClearsAmountDueAfterRecordedDeposit() {
        settlement.setDepositTotal(new BigDecimal("500000"));
        settlement.setFinalTotal(1_000_000L);
        settlement.setAmount(new BigDecimal("500000"));
        settlement.setAdditionalSurchargeTotal(0);
        settlement.setLateCheckoutFee(0);
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of());

        InvoiceData data = service.getInvoiceData(44L);

        assertThat(data.finalTotal()).isEqualByComparingTo("1000000");
        assertThat(data.depositTotal()).isEqualByComparingTo("500000");
        assertThat(data.settlementAmount()).isEqualByComparingTo("500000");
        assertThat(data.remainingAmount()).isEqualByComparingTo("0");
        assertThat(data.checkoutPayment()).isEqualByComparingTo("500000");
        assertThat(data.totalPaid()).isEqualByComparingTo("1000000");
        assertThat(data.lineItems().get(7).amount()).isEqualByComparingTo("0");
        assertThat(data.lineItems().get(7).description()).isEqualTo("Còn phải thu");
        assertThat(data.lineItems().get(8).description()).isEqualTo("Đã thanh toán đủ");
    }

    @Test
    void representsUnpaidBalanceInsteadOfClaimingItWasPaid() {
        settlement.setDepositTotal(new BigDecimal("500000"));
        settlement.setFinalTotal(1_000_000L);
        settlement.setAmount(new BigDecimal("300000"));
        settlement.setAdditionalSurchargeTotal(0);
        settlement.setLateCheckoutFee(0);
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of());

        InvoiceData data = service.getInvoiceData(44L);
        assertThat(data.checkoutPayment()).isEqualByComparingTo("300000");
        assertThat(data.totalPaid()).isEqualByComparingTo("800000");
        assertThat(data.remainingAmount()).isEqualByComparingTo("200000");
        assertThat(data.lineItems().get(7).description()).isEqualTo("Còn phải thu");
        assertThat(data.lineItems().get(7).amount()).isEqualByComparingTo("200000");
    }
    @Test
    void rejectsRowsThatDoNotMatchPersistedSettlementTotals() {
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L))
                .thenReturn(List.of(surcharge(SurchargeType.THEM_NGUOI, 100_000)));
        assertThatThrownBy(() -> service.getInvoiceData(44L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("không khớp additionalSurchargeTotal");
    }

    @Test
    void supplementsOnlyMissingHistoricalContactFieldsFromCurrentSettingsWithoutMutatingEitherVersion() {
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        OperatingSettings historical = settingsWithAddress();
        historical.setAddress(null);
        historical.setPhone("090-HISTORIC");
        OperatingSettings current = settingsWithAddress();
        current.setAddress("Current address");
        current.setPhone("090-CURRENT");
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(historical));
        when(settingsRepository.findFirstByOrderByCreatedAtDescIdDesc()).thenReturn(Optional.of(current));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of(
                surcharge(SurchargeType.THEM_NGUOI, 100_000), surcharge(SurchargeType.THEM_GIUONG, 100_000)));

        InvoiceData data = service.getInvoiceData(44L);

        assertThat(data.homestay().name()).isEqualTo(historical.getHomestayName());
        assertThat(data.homestay().address()).isEqualTo("Current address");
        assertThat(data.homestay().phone()).isEqualTo("090-HISTORIC");
        assertThat(data.contactInfoSupplementedAtExport()).isTrue();
        assertThat(historical.getAddress()).isNull();
        assertThat(historical.getPhone()).isEqualTo("090-HISTORIC");
        assertThat(current.getAddress()).isEqualTo("Current address");
        assertThat(current.getPhone()).isEqualTo("090-CURRENT");
        assertThat(settlement.getOperatingSettingsId()).isEqualTo(7L);
        verify(settingsRepository, never()).save(any(OperatingSettings.class));
        verify(settlementRepository, never()).save(any(BookingSettlement.class));
    }

    @Test
    void rejectsContactFieldsStillMissingFromCurrentSettings() {
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        OperatingSettings historical = settingsWithAddress();
        historical.setAddress(null);
        historical.setPhone(null);
        OperatingSettings current = settingsWithAddress();
        current.setAddress(" ");
        current.setPhone(null);
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(historical));
        when(settingsRepository.findFirstByOrderByCreatedAtDescIdDesc()).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.getInvoiceData(44L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("homestay");
        assertThat(historical.getAddress()).isNull();
        assertThat(historical.getPhone()).isNull();
        assertThat(current.getAddress()).isEqualTo(" ");
        assertThat(settlement.getOperatingSettingsId()).isEqualTo(7L);
    }

    @Test
    void rejectsMissingRequiredHomestayAddress() {
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        OperatingSettings settings = settingsWithAddress();
        settings.setAddress(null);
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settings));
        assertThatThrownBy(() -> service.getInvoiceData(44L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Thiếu địa chỉ homestay");
    }

    @Test
    void rejectsNonClosedBooking() {
        booking.setStatus(BookingStatus.DA_NHAN_PHONG);
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> service.getInvoiceData(44L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DA_TRA_PHONG");
    }

    @Test
    void doesNotIssueForCheckedInBookingEvenWhenSettlementLooksPaid() {
        booking.setStatus(BookingStatus.DA_NHAN_PHONG);
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.getInvoiceData(44L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DA_TRA_PHONG");
        verify(settlementRepository, never()).findByBookingId(44L);
    }

    @Test
    void rejectsEveryBookingStatusExceptCheckedOutBeforeReadingSettlement() {
        for (BookingStatus status : List.of(BookingStatus.CHO_XAC_NHAN, BookingStatus.DA_XAC_NHAN,
                BookingStatus.DA_NHAN_PHONG, BookingStatus.DA_HUY, BookingStatus.DA_HET_HAN,
                BookingStatus.NO_SHOW)) {
            booking.setStatus(status);
            when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> service.getInvoiceData(44L))
                    .as("status %s must not be invoiced", status)
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("DA_TRA_PHONG");
            verify(settlementRepository, never()).findByBookingId(44L);
        }
    }

    @Test
    void allowsCheckedOutBookingWithValidSettlement() {
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of(
                surcharge(SurchargeType.THEM_NGUOI, 100_000), surcharge(SurchargeType.THEM_GIUONG, 100_000)));

        InvoiceData data = service.getInvoiceData(44L);

        assertThat(data.bookingId()).isEqualTo(44L);
        assertThat(data.finalTotal()).isEqualByComparingTo("1300000");
    }

    @Test
    void rejectsCheckedOutBookingWithoutConfirmedSettlementBeforeLoadingInvoiceDetails() {
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        settlement.setSettlementType(BookingSettlement.SettlementType.COLLECT);
        for (String transactionStatus : List.of("NOT_APPLICABLE", "FAILED", "CANCELLED")) {
            settlement.setTransactionStatus(transactionStatus);
            when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));

            assertThatThrownBy(() -> service.getInvoiceData(44L))
                    .as("transaction status %s must not be invoiced", transactionStatus)
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("chưa xác nhận hoàn tất thu/hoàn tiền");
        }

        settlement.setTransactionStatus(null);
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        assertThatThrownBy(() -> service.getInvoiceData(44L))
                .as("missing transaction status must not be invoiced")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("chưa xác nhận hoàn tất thu/hoàn tiền");

        verify(settingsRepository, never()).findById(7L);
        verify(surchargeRepository, never()).findByBookingIdOrderByCreatedAtAscIdAsc(44L);
    }

    @Test
    void allowsZeroSettlementWithNotApplicableTransactionStatus() {
        settlement.setSettlementType(BookingSettlement.SettlementType.ZERO);
        settlement.setDepositTotal(BigDecimal.valueOf(1_300_000));
        settlement.setAmount(BigDecimal.ZERO);
        settlement.setTransactionStatus("NOT_APPLICABLE");
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of(
                surcharge(SurchargeType.THEM_NGUOI, 100_000), surcharge(SurchargeType.THEM_GIUONG, 100_000)));

        InvoiceData data = service.getInvoiceData(44L);

        assertThat(data.bookingId()).isEqualTo(44L);
        assertThat(data.settlementType()).isEqualTo(BookingSettlement.SettlementType.ZERO);
    }

    @Test
    void rejectsInvalidSettlementTypeAndTransactionStatusPairs() {
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        for (BookingSettlement.SettlementType type : List.of(
                BookingSettlement.SettlementType.COLLECT, BookingSettlement.SettlementType.REFUND)) {
            settlement.setSettlementType(type);
            settlement.setTransactionStatus("NOT_APPLICABLE");
            assertThatThrownBy(() -> service.getInvoiceData(44L))
                    .as("%s must not use NOT_APPLICABLE", type)
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("chưa xác nhận hoàn tất thu/hoàn tiền");
        }
        settlement.setSettlementType(BookingSettlement.SettlementType.ZERO);
        settlement.setTransactionStatus("CONFIRMED");
        assertThatThrownBy(() -> service.getInvoiceData(44L))
                .as("ZERO must not use CONFIRMED")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("chưa xác nhận hoàn tất thu/hoàn tiền");
    }

    @Test
    void rejectsCheckedOutBookingWithoutSettlement() {
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInvoiceData(44L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("(settlement)");
    }

    @Test
    void rejectsCheckoutCollectionGreaterThanOutstandingAmount() {
        settlement.setAmount(BigDecimal.valueOf(1_000_001));
        when(bookingRepository.findById(44L)).thenReturn(Optional.of(booking));
        when(settlementRepository.findByBookingId(44L)).thenReturn(Optional.of(settlement));
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settingsWithAddress()));
        when(surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(44L)).thenReturn(List.of(
                surcharge(SurchargeType.THEM_NGUOI, 100_000), surcharge(SurchargeType.THEM_GIUONG, 100_000)));
        assertThatThrownBy(() -> service.getInvoiceData(44L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("finalTotal");
    }
    private static BookingSurcharge surcharge(SurchargeType type, long unitPrice, long defaultUnitPrice, long amount,
            String overrideReason) {
        BookingSurcharge surcharge = new BookingSurcharge();
        surcharge.setSurchargeType(type);
        surcharge.setUnitPrice(unitPrice);
        surcharge.setDefaultUnitPrice(defaultUnitPrice);
        surcharge.setPriceOverrideReason(overrideReason);
        surcharge.setAmount(amount);
        return surcharge;
    }

    private static BookingSurcharge surcharge(SurchargeType type, long amount) {
        return surcharge(type, amount, amount, amount, null);
    }

    private static OperatingSettings settingsWithAddress() {
        OperatingSettings settings = new OperatingSettings();
        settings.setId(7L);
        settings.setHomestayName("Nhà nghỉ");
        settings.setAddress("123 Đường A");
        settings.setPhone("0901234567");
        return settings;
    }
}
