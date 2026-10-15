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
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.BookingSettlementRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    @Mock RoomStatusHistoryRepository historyRepository;
    @Mock BookingDepositService depositService;

    private CheckoutService service;
    private Room room;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new CheckoutService(roomRepository, bookingRepository, settlementRepository,
                historyRepository, depositService);
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
    void noDepositCollectsFullAmount() {
        CheckoutPreviewResponse preview = service.preview(12L);
        service.checkout(12L, new CheckoutPaymentRequest(preview.previewToken(), "CASH", null, true, false), "Lễ tân");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_TRA_PHONG);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.TRONG_BAN);
        verify(settlementRepository).save(any(BookingSettlement.class));
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
        verify(settlementRepository).save(any(BookingSettlement.class));
    }

    @Test
    void previewUsesCommittedNightSnapshotsAndDoesNotLookUpCurrentPrices() {
        BookingNightlyPriceSnapshot first = nightly(LocalDate.of(2026, 10, 10), 400_000, "Giá chốt đêm 1");
        BookingNightlyPriceSnapshot second = nightly(LocalDate.of(2026, 10, 11), 600_000, "Giá chốt đêm 2");
        booking.setNightlyPriceSnapshots(List.of(first, second));
        CheckoutPreviewResponse preview = service.preview(12L);
        assertThat(preview.nightlyBreakdownReliable()).isTrue();
        assertThat(preview.nightlyPrices()).extracting(CheckoutPreviewResponse.NightlyLine::roomPrice)
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

    private BookingNightlyPriceSnapshot nightly(LocalDate date, long price, String label) {
        BookingNightlyPriceSnapshot line = new BookingNightlyPriceSnapshot();
        line.setBooking(booking);
        line.setNightDate(date);
        line.setPriceType("OVERRIDE");
        line.setPriceLabel(label);
        line.setRoomPrice(price);
        line.setSurchargeAmount(0);
        return line;
    }
}
