package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingCreateRequest;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.BookingListItemResponse;
import com.ttcs.homestay.dto.booking.BookingUpdateRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.PriceOverride;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.PriceOverrideRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private OperatingSettingsService operatingSettingsService;

        @Mock
    private PriceOverrideRepository priceOverrideRepository;

    @Mock
    private BookingDepositService bookingDepositService;

    @Mock
    private AuditLogService auditLogService;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        // S2-02 Lát 2: dùng PricingService thật để kiểm tra đúng cách tính giá, chỉ giả lập repository.
        PricingService pricingService =
                new PricingService(roomTypeRepository, priceOverrideRepository, operatingSettingsService);
        bookingService = new BookingService(
                bookingRepository, roomTypeRepository, operatingSettingsService, pricingService, bookingDepositService,
                auditLogService);
    }

    @Test
    void bookingDaTaoGiuNguyenSnapshotSauKhiGiaThayDoi_bookingMoiDungGiaMoi() {
        RoomType roomType = new RoomType();
        roomType.setId(4L);
        roomType.setName("Phòng đôi");
        roomType.setStatus(true);
        roomType.setWeekdayPrice(100_000L);
        roomType.setWeekendPrice(200_000L);
        when(roomTypeRepository.findById(4L)).thenReturn(Optional.of(roomType));

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("FRIDAY,SATURDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);

        List<Booking> storedBookings = new ArrayList<>();
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId((long) storedBookings.size() + 1);
            storedBookings.add(booking);
            return booking;
        });

        BookingCreateRequest request = new BookingCreateRequest(
            4L,
            "Nguyễn Văn A",
            LocalDate.of(2026, 10, 2),
            LocalDate.of(2026, 10, 5)
        );
        BookingResponse originalBooking = bookingService.createBooking(request);

        roomType.setWeekdayPrice(150_000L);
        roomType.setWeekendPrice(300_000L);
        settings.setWeekendDays("SUNDAY");
        BookingResponse newBooking = bookingService.createBooking(request);

        assertThat(originalBooking.weekdayPriceSnapshot()).isEqualTo(100_000L);
        assertThat(originalBooking.weekendPriceSnapshot()).isEqualTo(200_000L);
        assertThat(originalBooking.weekendDaysSnapshot()).isEqualTo("FRIDAY,SATURDAY");
        assertThat(originalBooking.totalAmount()).isEqualTo(500_000L);
        assertThat(storedBookings.get(0).getTotalAmount()).isEqualTo(500_000L);

        assertThat(newBooking.weekdayPriceSnapshot()).isEqualTo(150_000L);
        assertThat(newBooking.weekendPriceSnapshot()).isEqualTo(300_000L);
        assertThat(newBooking.weekendDaysSnapshot()).isEqualTo("SUNDAY");
        assertThat(newBooking.totalAmount()).isEqualTo(600_000L);
        assertThat(storedBookings.get(1).getTotalAmount()).isEqualTo(600_000L);
    }

    @Test
    void getLatestBookingsUsesCreatedAtDescendingOrder() {
        Booking newest = new Booking();
        newest.setBookingCode("BK-NEW");
        newest.setGuestName("Khách mới");
        newest.setRoomTypeNameSnapshot("Phòng đôi");
        newest.setCheckInDate(LocalDate.of(2026, 10, 20));
        newest.setCheckOutDate(LocalDate.of(2026, 10, 22));
        newest.setTotalAmount(1_000_000L);
        newest.setStatus(BookingStatus.CHO_XAC_NHAN);

        Booking oldest = new Booking();
        oldest.setBookingCode("BK-OLD");
        oldest.setGuestName("Khách cũ");
        oldest.setRoomTypeNameSnapshot("Phòng đơn");
        oldest.setCheckInDate(LocalDate.of(2026, 10, 18));
        oldest.setCheckOutDate(LocalDate.of(2026, 10, 19));
        oldest.setTotalAmount(500_000L);
        oldest.setStatus(BookingStatus.DA_XAC_NHAN);

        when(bookingRepository.findAllByOrderByCreatedAtDescIdDesc())
                .thenReturn(List.of(newest, oldest));

        var result = bookingService.getLatestBookings();

        assertThat(result)
                .extracting(BookingListItemResponse::bookingCode)
                .containsExactly("BK-NEW", "BK-OLD");
    }

    @Test
    void bookingXuyenSuot3LoaiGia_tongTienDungUuTienGiaDe() {
        RoomType roomType = new RoomType();
        roomType.setId(4L);
        roomType.setName("Phòng đôi");
        roomType.setStatus(true);
        roomType.setWeekdayPrice(100_000L);
        roomType.setWeekendPrice(200_000L);
        when(roomTypeRepository.findById(4L)).thenReturn(Optional.of(roomType));

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("FRIDAY,SATURDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);

        PriceOverride le304 = new PriceOverride();
        le304.setName("Lễ 30/4");
        le304.setStartDate(LocalDate.of(2027, 4, 29));
        le304.setEndDate(LocalDate.of(2027, 5, 1));
        le304.setPricePerNight(900_000L);
        when(priceOverrideRepository.findOverlapping(anyLong(), any(), any())).thenReturn(List.of(le304));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 28/04 thứ Tư 100.000 | 29/04, 30/04 (thứ Sáu), 01/05 (thứ Bảy) giá đè 900.000 | 02/05 Chủ nhật 100.000
        BookingResponse booking = bookingService.createBooking(new BookingCreateRequest(
                4L, "Khách Lễ", LocalDate.of(2027, 4, 28), LocalDate.of(2027, 5, 3)));

        assertThat(booking.totalAmount()).isEqualTo(2_900_000L);
    }

    @Test
    void doiNgayNhanVaNgayTraPhongThanhCong_capNhatDungThongTinVaGiuNguyenState() {
        RoomType roomType = new RoomType();
        roomType.setId(1L);
        roomType.setName("Phòng Deluxe");

        Booking existing = new Booking();
        existing.setId(10L);
        existing.setBookingCode("BK-1001");
        existing.setGuestName("Trần Văn B");
        existing.setStatus(BookingStatus.DA_XAC_NHAN);
        existing.setRoomType(roomType);
        existing.setRoomTypeNameSnapshot(roomType.getName());
        existing.setCheckInDate(LocalDate.of(2027, 5, 1));
        existing.setCheckOutDate(LocalDate.of(2027, 5, 5));
        existing.setTotalAmount(2_000_000L);

        when(bookingRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(roomType));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate newCheckIn = LocalDate.of(2027, 5, 10);
        LocalDate newCheckOut = LocalDate.of(2027, 5, 15);
        BookingUpdateRequest request = new BookingUpdateRequest(1L, newCheckIn, newCheckOut);

        BookingResponse response = bookingService.updateBooking(10L, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.checkInDate()).isEqualTo(newCheckIn);
        assertThat(response.checkOutDate()).isEqualTo(newCheckOut);
        assertThat(response.roomTypeId()).isEqualTo(1L);
        assertThat(response.roomTypeNameSnapshot()).isEqualTo("Phòng Deluxe");
        assertThat(response.totalAmount()).isEqualTo(2_000_000L);
        assertThat(response.status()).isEqualTo(BookingStatus.DA_XAC_NHAN);
    }

    @Test
    void doiLoaiPhongThanhCong_capNhatSnapshotTenLoaiPhongMoi() {
        RoomType oldRoomType = new RoomType();
        oldRoomType.setId(1L);
        oldRoomType.setName("Phòng Đơn");

        RoomType newRoomType = new RoomType();
        newRoomType.setId(2L);
        newRoomType.setName("Phòng Suite VIP");

        Booking existing = new Booking();
        existing.setId(20L);
        existing.setBookingCode("BK-2002");
        existing.setGuestName("Lê Thị C");
        existing.setStatus(BookingStatus.CHO_XAC_NHAN);
        existing.setRoomType(oldRoomType);
        existing.setRoomTypeNameSnapshot(oldRoomType.getName());
        existing.setCheckInDate(LocalDate.of(2027, 6, 1));
        existing.setCheckOutDate(LocalDate.of(2027, 6, 4));
        existing.setTotalAmount(1_200_000L);

        when(bookingRepository.findById(20L)).thenReturn(Optional.of(existing));
        when(roomTypeRepository.findById(2L)).thenReturn(Optional.of(newRoomType));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingUpdateRequest request = new BookingUpdateRequest(2L, existing.getCheckInDate(), existing.getCheckOutDate());
        BookingResponse response = bookingService.updateBooking(20L, request);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.roomTypeId()).isEqualTo(2L);
        assertThat(response.roomTypeNameSnapshot()).isEqualTo("Phòng Suite VIP");
        assertThat(response.checkInDate()).isEqualTo(existing.getCheckInDate());
        assertThat(response.checkOutDate()).isEqualTo(existing.getCheckOutDate());
    }

    @Test
    void doiDongThoiCaNgayVaLoaiPhongThanhCong() {
        RoomType oldRoomType = new RoomType();
        oldRoomType.setId(1L);
        oldRoomType.setName("Phòng Đơn");

        RoomType newRoomType = new RoomType();
        newRoomType.setId(3L);
        newRoomType.setName("Phòng Gia Đình");

        Booking existing = new Booking();
        existing.setId(30L);
        existing.setBookingCode("BK-3003");
        existing.setGuestName("Phạm Văn D");
        existing.setStatus(BookingStatus.DA_XAC_NHAN);
        existing.setRoomType(oldRoomType);
        existing.setRoomTypeNameSnapshot(oldRoomType.getName());
        existing.setCheckInDate(LocalDate.of(2027, 7, 10));
        existing.setCheckOutDate(LocalDate.of(2027, 7, 14));
        existing.setTotalAmount(3_000_000L);

        when(bookingRepository.findById(30L)).thenReturn(Optional.of(existing));
        when(roomTypeRepository.findById(3L)).thenReturn(Optional.of(newRoomType));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate newCheckIn = LocalDate.of(2027, 8, 1);
        LocalDate newCheckOut = LocalDate.of(2027, 8, 7);
        BookingUpdateRequest request = new BookingUpdateRequest(3L, newCheckIn, newCheckOut);

        BookingResponse response = bookingService.updateBooking(30L, request);

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.checkInDate()).isEqualTo(newCheckIn);
        assertThat(response.checkOutDate()).isEqualTo(newCheckOut);
        assertThat(response.roomTypeId()).isEqualTo(3L);
        assertThat(response.roomTypeNameSnapshot()).isEqualTo("Phòng Gia Đình");
        assertThat(response.bookingCode()).isEqualTo("BK-3003");
        assertThat(response.guestName()).isEqualTo("Phạm Văn D");
    }

    @Test
    void nhapNgayKhongHopLe_ngayNhanBangHoacSauNgayTra_nemLoiBadRequest() {
        // Kịch bản 1: ngày nhận bằng ngày trả phòng
        BookingUpdateRequest sameDatesRequest = new BookingUpdateRequest(
                1L,
                LocalDate.of(2027, 9, 10),
                LocalDate.of(2027, 9, 10)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.updateBooking(1L, sameDatesRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Ngày trả phòng phải sau ngày nhận phòng");

        // Kịch bản 2: ngày nhận lớn hơn ngày trả phòng
        BookingUpdateRequest reversedDatesRequest = new BookingUpdateRequest(
                1L,
                LocalDate.of(2027, 9, 15),
                LocalDate.of(2027, 9, 10)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.updateBooking(1L, reversedDatesRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Ngày trả phòng phải sau ngày nhận phòng");

        org.mockito.Mockito.verify(bookingRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void nhapThongTinThieu_loaiPhongHoacNgayNull_nemLoiBadRequest() {
        // null request
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.updateBooking(1L, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Yêu cầu không được để trống");

        // null roomTypeId
        BookingUpdateRequest nullRoomTypeRequest = new BookingUpdateRequest(
                null,
                LocalDate.of(2027, 9, 1),
                LocalDate.of(2027, 9, 5)
        );
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.updateBooking(1L, nullRoomTypeRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Loại phòng không được để trống");

        // null checkInDate
        BookingUpdateRequest nullCheckInRequest = new BookingUpdateRequest(
                1L,
                null,
                LocalDate.of(2027, 9, 5)
        );
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.updateBooking(1L, nullCheckInRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Ngày trả phòng phải sau ngày nhận phòng");

        // null checkOutDate
        BookingUpdateRequest nullCheckOutRequest = new BookingUpdateRequest(
                1L,
                LocalDate.of(2027, 9, 1),
                null
        );
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.updateBooking(1L, nullCheckOutRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Ngày trả phòng phải sau ngày nhận phòng");
    }

    @Test
    void kiemTraDuLieuBookingSauKhiLuu_damBaoCapNhatChinhXacTrongState() {
        RoomType oldRoomType = new RoomType();
        oldRoomType.setId(5L);
        oldRoomType.setName("Phòng Cũ");

        RoomType newRoomType = new RoomType();
        newRoomType.setId(6L);
        newRoomType.setName("Phòng Mới");

        Booking existing = new Booking();
        existing.setId(50L);
        existing.setBookingCode("BK-5005");
        existing.setGuestName("Hoàng Văn E");
        existing.setStatus(BookingStatus.DA_XAC_NHAN);
        existing.setRoomType(oldRoomType);
        existing.setRoomTypeNameSnapshot(oldRoomType.getName());
        existing.setCheckInDate(LocalDate.of(2027, 10, 1));
        existing.setCheckOutDate(LocalDate.of(2027, 10, 5));
        existing.setTotalAmount(5_000_000L);
        existing.setWeekdayPriceSnapshot(1_000_000L);
        existing.setWeekendPriceSnapshot(1_500_000L);

        when(bookingRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(roomTypeRepository.findById(6L)).thenReturn(Optional.of(newRoomType));

        org.mockito.ArgumentCaptor<Booking> captor = org.mockito.ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        LocalDate newCheckIn = LocalDate.of(2027, 11, 10);
        LocalDate newCheckOut = LocalDate.of(2027, 11, 15);
        bookingService.updateBooking(50L, new BookingUpdateRequest(6L, newCheckIn, newCheckOut));

        Booking saved = captor.getValue();
        // Kiểm tra thông tin đã cập nhật
        assertThat(saved.getCheckInDate()).isEqualTo(newCheckIn);
        assertThat(saved.getCheckOutDate()).isEqualTo(newCheckOut);
        assertThat(saved.getRoomType().getId()).isEqualTo(6L);
        assertThat(saved.getRoomTypeNameSnapshot()).isEqualTo("Phòng Mới");

        // Kiểm tra thông tin gốc không bị biến đổi/mất mát
        assertThat(saved.getId()).isEqualTo(50L);
        assertThat(saved.getBookingCode()).isEqualTo("BK-5005");
        assertThat(saved.getGuestName()).isEqualTo("Hoàng Văn E");
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        assertThat(saved.getTotalAmount()).isEqualTo(5_000_000L);
        assertThat(saved.getWeekdayPriceSnapshot()).isEqualTo(1_000_000L);
        assertThat(saved.getWeekendPriceSnapshot()).isEqualTo(1_500_000L);
    }
}
