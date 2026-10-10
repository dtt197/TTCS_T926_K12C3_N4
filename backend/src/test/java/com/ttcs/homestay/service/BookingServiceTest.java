package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingChangePreviewResponse;
import com.ttcs.homestay.dto.booking.BookingCreateRequest;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.BookingUpdateRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.PriceOverride;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.exception.RoomUnavailableException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.PriceOverrideRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.EntityManager;
import com.ttcs.homestay.entity.User;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import org.mockito.ArgumentCaptor;
import com.ttcs.homestay.dto.booking.BookingAuditLogResponse;
import com.ttcs.homestay.entity.BookingAuditLog;
import com.ttcs.homestay.repository.BookingAuditLogRepository;
import com.ttcs.homestay.repository.BookingRoomChangeHistoryRepository;
import com.ttcs.homestay.repository.RoomRepository;

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

    @Mock
    private RoomAvailabilityService roomAvailabilityService;

    @Mock
    private BookingAuditLogRepository bookingAuditLogRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRoomChangeHistoryRepository roomChangeHistoryRepository;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        // S2-02 Lát 2: dùng PricingService thật để kiểm tra đúng cách tính giá, chỉ giả lập repository.
        PricingService pricingService =
                new PricingService(roomTypeRepository, priceOverrideRepository, operatingSettingsService);
        bookingService = new BookingService(
                bookingRepository, roomTypeRepository, operatingSettingsService, pricingService, bookingDepositService,
                auditLogService, roomAvailabilityService, bookingAuditLogRepository, roomRepository,
                roomChangeHistoryRepository);
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
        assertThat(storedBookings.get(0).getRoom()).isNull();

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
                .extracting(item -> item.bookingCode())
                .containsExactly("BK-NEW", "BK-OLD");
    }

    @Test
    void assignRoom_bookingDaXacNhanPhongHopLe_thanhCong() {
        authenticateAs(55L);
        RoomType type = new RoomType();
        type.setId(4L);
        type.setName("Phòng đôi");
        Room room = new Room();
        room.setId(9L);
        room.setRoomNumber("201");
        room.setRoomType("Phòng đôi");
        room.setFloor(2);
        room.setStatus(RoomStatus.TRONG_SACH);
        room.setActive(true);
        Booking booking = new Booking();
        booking.setId(7L);
        booking.setBookingCode("BK-7");
        booking.setGuestName("Khách A");
        booking.setRoomType(type);
        booking.setRoomTypeNameSnapshot(type.getName());
        booking.setCheckInDate(LocalDate.of(2027, 6, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 6, 12));
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setCreatedAt(java.time.OffsetDateTime.now());
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(room));
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findRoomConflicts(9L, 7L, booking.getCheckInDate(), booking.getCheckOutDate(),
                RoomAvailabilityService.OCCUPYING_STATUSES)).thenReturn(List.of());
        when(roomAvailabilityService.isRoomAvailable(room, type, booking.getCheckInDate(), booking.getCheckOutDate(), 7L))
                .thenReturn(true);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.assignRoom(7L, 9L);

        assertThat(response.roomNumber()).isEqualTo("201");
        assertThat(booking.getRoom()).isSameAs(room);
        verify(bookingRepository).flush();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(userId.toString())
                .claim("email", "receptionist@homestay.local").claim("fullName", "Receptionist").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        EntityManager entityManager = Mockito.mock(EntityManager.class);
        Mockito.when(entityManager.getReference(User.class, userId)).thenReturn(Mockito.mock(User.class));
        ReflectionTestUtils.setField(bookingService, "entityManager", entityManager);
    }

    @Test
    void assignRoom_coXungDotTraVeTatCaMaBooking_vaKhongSuaBooking() {
        RoomType type = new RoomType();
        type.setId(4L);
        type.setName("Phòng đôi");
        Room room = new Room();
        room.setId(9L);
        room.setRoomNumber("201");
        Booking booking = new Booking();
        booking.setId(7L);
        booking.setBookingCode("BK-CURRENT");
        booking.setRoomType(type);
        booking.setCheckInDate(LocalDate.of(2027, 6, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 6, 13));
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        Booking conflictB = new Booking();
        conflictB.setBookingCode("BK-902");
        conflictB.setStatus(BookingStatus.DA_XAC_NHAN);
        Booking conflictA = new Booking();
        conflictA.setBookingCode("BK-901");
        conflictA.setStatus(BookingStatus.DA_NHAN_PHONG);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(room));
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findRoomConflicts(eq(9L), eq(7L), any(), any(), any()))
                .thenReturn(List.of(conflictB, conflictA));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.assignRoom(7L, 9L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("BK-901, BK-902");
        assertThat(booking.getRoom()).isNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void assignRoom_saiTrangThaiBiTuChoi() {
        Room room = new Room();
        room.setId(9L);
        Booking booking = new Booking();
        booking.setId(7L);
        RoomType type = new RoomType();
        type.setId(4L);
        booking.setRoomType(type);
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(room));
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.assignRoom(7L, 9L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void assignRoom_phongGiuTamCoTheDoiSangPhongTrongKhac() {
        Room existingRoom = new Room();
        existingRoom.setId(10L);
        existingRoom.setRoomNumber("202");
        Room differentRoom = new Room();
        differentRoom.setId(9L);
        differentRoom.setRoomNumber("201");
        differentRoom.setRoomType("Phòng đôi");
        differentRoom.setActive(true);
        differentRoom.setStatus(RoomStatus.TRONG_SACH);
        RoomType type = new RoomType();
        type.setId(4L);
        type.setName("Phòng đôi");
        authenticateAs(55L);
        when(roomRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(differentRoom));
        Booking booking = new Booking();
        booking.setId(7L);
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setRoomType(type);
        booking.setCheckInDate(LocalDate.of(2027, 6, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 6, 12));
        booking.setRoom(existingRoom);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findRoomConflicts(eq(9L), eq(7L), any(), any(), any())).thenReturn(List.of());
        when(roomAvailabilityService.isRoomAvailable(differentRoom, type, booking.getCheckInDate(),
                booking.getCheckOutDate(), 7L)).thenReturn(true);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.assignRoom(7L, 9L);

        assertThat(response.roomNumber()).isEqualTo("201");
        assertThat(booking.getRoom()).isSameAs(differentRoom);
        assertThat(booking.getRoomConfirmedAt()).isNotNull();
        assertThat(booking.getRoomConfirmedByUser()).isNotNull();
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
        roomType.setStatus(true);
        roomType.setWeekdayPrice(400_000L);
        roomType.setWeekendPrice(400_000L);

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("SATURDAY,SUNDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(roomAvailabilityService.availableRooms(any(), any(), any(), any())).thenReturn(2);

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

        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
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
        newRoomType.setStatus(true);
        newRoomType.setWeekdayPrice(400_000L);
        newRoomType.setWeekendPrice(400_000L);

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("SATURDAY,SUNDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(roomAvailabilityService.availableRooms(any(), any(), any(), any())).thenReturn(1);

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

        when(bookingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(existing));
        when(roomTypeRepository.findById(2L)).thenReturn(Optional.of(newRoomType));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingUpdateRequest request = new BookingUpdateRequest(2L, existing.getCheckInDate(), existing.getCheckOutDate());
        BookingResponse response = bookingService.updateBooking(20L, request);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.roomTypeId()).isEqualTo(2L);
        assertThat(response.roomTypeNameSnapshot()).isEqualTo("Phòng Suite VIP");
        assertThat(response.checkInDate()).isEqualTo(existing.getCheckInDate());
        assertThat(response.checkOutDate()).isEqualTo(existing.getCheckOutDate());
        assertThat(response.totalAmount()).isEqualTo(1_200_000L);
    }

    @Test
    void doiDongThoiCaNgayVaLoaiPhongThanhCong() {
        RoomType oldRoomType = new RoomType();
        oldRoomType.setId(1L);
        oldRoomType.setName("Phòng Đơn");

        RoomType newRoomType = new RoomType();
        newRoomType.setId(3L);
        newRoomType.setName("Phòng Gia Đình");
        newRoomType.setStatus(true);
        newRoomType.setWeekdayPrice(500_000L);
        newRoomType.setWeekendPrice(500_000L);

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("SATURDAY,SUNDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(roomAvailabilityService.availableRooms(any(), any(), any(), any())).thenReturn(3);

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

        when(bookingRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(existing));
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
        assertThat(response.totalAmount()).isEqualTo(3_000_000L); // 6 đêm * 500k = 3.000k
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
        newRoomType.setStatus(true);
        newRoomType.setWeekdayPrice(1_000_000L);
        newRoomType.setWeekendPrice(1_000_000L);

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("SATURDAY,SUNDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(roomAvailabilityService.availableRooms(any(), any(), any(), any())).thenReturn(2);

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

        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(existing));
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
        assertThat(saved.getTotalAmount()).isEqualTo(5_000_000L); // 5 đêm * 1.000k
        assertThat(saved.getWeekdayPriceSnapshot()).isEqualTo(1_000_000L);
        assertThat(saved.getWeekendPriceSnapshot()).isEqualTo(1_000_000L);
    }

    @Test
    void thayDoiSangNgayConPhong_choPhepVaTinhLaiDungTien() {
        RoomType roomType = new RoomType();
        roomType.setId(10L);
        roomType.setName("Phòng Deluxe");
        roomType.setStatus(true);
        roomType.setWeekdayPrice(200_000L);
        roomType.setWeekendPrice(300_000L);

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("SATURDAY,SUNDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(roomAvailabilityService.availableRooms(eq(roomType), any(), any(), eq(100L))).thenReturn(3);

        Booking existing = new Booking();
        existing.setId(100L);
        existing.setBookingCode("BK-100");
        existing.setGuestName("Nguyễn Văn A");
        existing.setStatus(BookingStatus.DA_XAC_NHAN);
        existing.setRoomType(roomType);
        existing.setRoomTypeNameSnapshot("Phòng Deluxe");
        existing.setCheckInDate(LocalDate.of(2027, 10, 1)); // Thứ Sáu
        existing.setCheckOutDate(LocalDate.of(2027, 10, 3)); // 2 đêm: 200k + 300k = 500k
        existing.setTotalAmount(500_000L);

        when(bookingRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(existing));
        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(roomType));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Đổi sang: Thứ Sáu 2027-10-01 đến Thứ Hai 2027-10-04 (3 đêm: T6=200k, T7=300k, CN=300k -> 800k)
        LocalDate newCheckIn = LocalDate.of(2027, 10, 1);
        LocalDate newCheckOut = LocalDate.of(2027, 10, 4);
        BookingUpdateRequest request = new BookingUpdateRequest(10L, newCheckIn, newCheckOut);

        BookingResponse response = bookingService.updateBooking(100L, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.checkInDate()).isEqualTo(newCheckIn);
        assertThat(response.checkOutDate()).isEqualTo(newCheckOut);
        assertThat(response.totalAmount()).isEqualTo(800_000L);
    }

    @Test
    void thayDoiSangNgayHetPhong_biTuChoiVaNemLoiRoomUnavailableException() {
        RoomType roomType = new RoomType();
        roomType.setId(10L);
        roomType.setName("Phòng Deluxe");
        roomType.setStatus(true);

        Booking existing = new Booking();
        existing.setId(101L);
        existing.setBookingCode("BK-101");
        existing.setRoomType(roomType);
        existing.setCheckInDate(LocalDate.of(2027, 10, 1));
        existing.setCheckOutDate(LocalDate.of(2027, 10, 3));

        when(bookingRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(existing));
        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(roomType));
        // Giả lập hết phòng
        when(roomAvailabilityService.availableRooms(eq(roomType), any(), any(), eq(101L))).thenReturn(0);

        LocalDate newCheckIn = LocalDate.of(2027, 10, 5);
        LocalDate newCheckOut = LocalDate.of(2027, 10, 8);
        BookingUpdateRequest request = new BookingUpdateRequest(10L, newCheckIn, newCheckOut);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.updateBooking(101L, request))
                .isInstanceOf(RoomUnavailableException.class)
                .hasMessageContaining("đã hết phòng trống");

        org.mockito.Mockito.verify(bookingRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void doiSangLoaiPhongKhacConPhong_choPhepVaTinhLaiTienTheoGiaLoaiPhongMoi() {
        RoomType oldType = new RoomType();
        oldType.setId(1L);
        oldType.setName("Phòng Standard");

        RoomType newType = new RoomType();
        newType.setId(2L);
        newType.setName("Phòng VIP");
        newType.setStatus(true);
        newType.setWeekdayPrice(800_000L);
        newType.setWeekendPrice(1_200_000L);

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("SATURDAY,SUNDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(roomAvailabilityService.availableRooms(eq(newType), any(), any(), eq(102L))).thenReturn(1);

        Booking existing = new Booking();
        existing.setId(102L);
        existing.setBookingCode("BK-102");
        existing.setRoomType(oldType);
        existing.setRoomTypeNameSnapshot("Phòng Standard");
        existing.setCheckInDate(LocalDate.of(2027, 10, 11)); // Thứ Hai
        existing.setCheckOutDate(LocalDate.of(2027, 10, 13)); // Thứ Tư (2 đêm)
        existing.setTotalAmount(400_000L);

        when(bookingRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(existing));
        when(roomTypeRepository.findById(2L)).thenReturn(Optional.of(newType));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Giữ nguyên ngày hoặc đổi loại phòng: 2 đêm ngày thường * 800k = 1.600.000đ
        BookingUpdateRequest request = new BookingUpdateRequest(2L, existing.getCheckInDate(), existing.getCheckOutDate());
        BookingResponse response = bookingService.updateBooking(102L, request);

        assertThat(response.roomTypeId()).isEqualTo(2L);
        assertThat(response.roomTypeNameSnapshot()).isEqualTo("Phòng VIP");
        assertThat(response.totalAmount()).isEqualTo(1_600_000L);
    }

    @Test
    void doiSangLoaiPhongKhacHetPhong_biTuChoiVaNemLoiRoomUnavailableException() {
        RoomType oldType = new RoomType();
        oldType.setId(1L);
        oldType.setName("Phòng Standard");

        RoomType newType = new RoomType();
        newType.setId(2L);
        newType.setName("Phòng VIP");
        newType.setStatus(true);

        Booking existing = new Booking();
        existing.setId(103L);
        existing.setBookingCode("BK-103");
        existing.setRoomType(oldType);
        existing.setCheckInDate(LocalDate.of(2027, 10, 11));
        existing.setCheckOutDate(LocalDate.of(2027, 10, 13));

        when(bookingRepository.findByIdForUpdate(103L)).thenReturn(Optional.of(existing));
        when(roomTypeRepository.findById(2L)).thenReturn(Optional.of(newType));
        when(roomAvailabilityService.availableRooms(eq(newType), any(), any(), eq(103L))).thenReturn(0);

        BookingUpdateRequest request = new BookingUpdateRequest(2L, existing.getCheckInDate(), existing.getCheckOutDate());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.updateBooking(103L, request))
                .isInstanceOf(RoomUnavailableException.class)
                .hasMessageContaining("đã hết phòng trống");

        org.mockito.Mockito.verify(bookingRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void previewBookingChange_traVeDungSoDemTinhTrangPhongVaTongTien() {
        RoomType roomType = new RoomType();
        roomType.setId(10L);
        roomType.setName("Phòng Deluxe");
        roomType.setStatus(true);
        roomType.setWeekdayPrice(200_000L);
        roomType.setWeekendPrice(300_000L);

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("SATURDAY,SUNDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(roomAvailabilityService.availableRooms(eq(roomType), any(), any(), eq(105L))).thenReturn(2);

        Booking existing = new Booking();
        existing.setId(105L);
        existing.setBookingCode("BK-105");
        existing.setRoomType(roomType);
        existing.setCheckInDate(LocalDate.of(2027, 10, 1));
        existing.setCheckOutDate(LocalDate.of(2027, 10, 3));

        when(bookingRepository.findById(105L)).thenReturn(Optional.of(existing));
        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(roomType));

        LocalDate newCheckIn = LocalDate.of(2027, 10, 1); // Thứ Sáu: 200k
        LocalDate newCheckOut = LocalDate.of(2027, 10, 4); // Thứ Hai: T7=300k, CN=300k -> 800k total
        BookingUpdateRequest request = new BookingUpdateRequest(10L, newCheckIn, newCheckOut);

        BookingChangePreviewResponse preview = bookingService.previewBookingChange(105L, request);

        assertThat(preview.bookingId()).isEqualTo(105L);
        assertThat(preview.roomTypeId()).isEqualTo(10L);
        assertThat(preview.roomTypeName()).isEqualTo("Phòng Deluxe");
        assertThat(preview.numberOfNights()).isEqualTo(3);
        assertThat(preview.availableRooms()).isEqualTo(2);
        assertThat(preview.available()).isTrue();
        assertThat(preview.totalAmount()).isEqualTo(800_000L);
        assertThat(preview.nightlyPrices()).hasSize(3);
    }

    @Test
    void updateBooking_ghiNhanAuditLogDayDuGiaTriCuVaMoi() {
        RoomType oldRoomType = new RoomType();
        oldRoomType.setId(1L);
        oldRoomType.setName("Phòng Standard");
        oldRoomType.setStatus(true);
        oldRoomType.setWeekdayPrice(100_000L);
        oldRoomType.setWeekendPrice(100_000L);

        RoomType newRoomType = new RoomType();
        newRoomType.setId(2L);
        newRoomType.setName("Phòng VIP");
        newRoomType.setStatus(true);
        newRoomType.setWeekdayPrice(300_000L);
        newRoomType.setWeekendPrice(300_000L);

        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("FRIDAY,SATURDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);

        Booking existing = new Booking();
        existing.setId(200L);
        existing.setBookingCode("BK-200");
        existing.setRoomType(oldRoomType);
        existing.setRoomTypeNameSnapshot(oldRoomType.getName());
        existing.setCheckInDate(LocalDate.of(2027, 5, 1));
        existing.setCheckOutDate(LocalDate.of(2027, 5, 3));
        existing.setTotalAmount(200_000L);
        existing.setStatus(BookingStatus.DA_XAC_NHAN);
        existing.setRoom(null);

        when(bookingRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(existing));
        when(bookingDepositService.hasDeposit(existing)).thenReturn(false);
        when(roomTypeRepository.findById(2L)).thenReturn(Optional.of(newRoomType));
        when(roomAvailabilityService.availableRooms(eq(newRoomType), any(), any(), eq(200L))).thenReturn(3);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingUpdateRequest request = new BookingUpdateRequest(2L, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 4));
        BookingService.ActorInfo actor = new BookingService.ActorInfo(55L, "Lễ tân Hoa", "hoa@homestay.local");

        BookingResponse response = bookingService.updateBooking(200L, request, actor);

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.roomTypeId()).isEqualTo(2L);

        ArgumentCaptor<BookingAuditLog> captor = ArgumentCaptor.forClass(BookingAuditLog.class);
        verify(bookingAuditLogRepository).save(captor.capture());

        BookingAuditLog auditLog = captor.getValue();
        assertThat(auditLog.getBookingCode()).isEqualTo("BK-200");
        assertThat(auditLog.getOldCheckInDate()).isEqualTo(LocalDate.of(2027, 5, 1));
        assertThat(auditLog.getNewCheckInDate()).isEqualTo(LocalDate.of(2027, 6, 1));
        assertThat(auditLog.getOldCheckOutDate()).isEqualTo(LocalDate.of(2027, 5, 3));
        assertThat(auditLog.getNewCheckOutDate()).isEqualTo(LocalDate.of(2027, 6, 4));
        assertThat(auditLog.getOldRoomTypeId()).isEqualTo(1L);
        assertThat(auditLog.getNewRoomTypeId()).isEqualTo(2L);
        assertThat(auditLog.getOldRoomTypeName()).isEqualTo("Phòng Standard");
        assertThat(auditLog.getNewRoomTypeName()).isEqualTo("Phòng VIP");
        assertThat(auditLog.getOldTotalAmount()).isEqualTo(200_000L);
        assertThat(auditLog.getNewTotalAmount()).isEqualTo(900_000L); // 3 đêm * 300k
        assertThat(auditLog.getActorUserId()).isEqualTo(55L);
        assertThat(auditLog.getActorName()).isEqualTo("Lễ tân Hoa");
        assertThat(auditLog.getActorEmail()).isEqualTo("hoa@homestay.local");
        assertThat(auditLog.getCreatedAt()).isNotNull();
    }

    @Test
    void createBooking_giuSuatLoaiPhongNhungKhongTuGanPhong() {
        RoomType roomType = new RoomType();
        roomType.setId(41L);
        roomType.setName("Phòng Lượt 1");
        roomType.setStatus(true);
        roomType.setWeekdayPrice(100_000L);
        roomType.setWeekendPrice(100_000L);
        when(roomTypeRepository.findById(41L)).thenReturn(Optional.of(roomType));
        when(roomTypeRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(roomType));
        OperatingSettings settings = new OperatingSettings();
        settings.setWeekendDays("SATURDAY,SUNDAY");
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(settings);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookingService.createBooking(new BookingCreateRequest(
                41L, "Khách Lượt 1", LocalDate.of(2027, 10, 1), LocalDate.of(2027, 10, 3)));

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(saved.capture());
        assertThat(saved.getValue().getRoom()).isNull();
        verify(roomAvailabilityService).assignRoom(any(), any(), any(), any(), any());
    }

    @Test
    void updateBooking_bookingChuaGanPhongTiepTucChuaGan() {
        RoomType roomType = new RoomType();
        roomType.setId(51L);
        roomType.setName("Phòng chưa gán");
        roomType.setStatus(true);
        roomType.setWeekdayPrice(100_000L);
        roomType.setWeekendPrice(100_000L);
        Booking booking = new Booking();
        booking.setId(151L);
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setRoomType(roomType);
        booking.setRoomTypeNameSnapshot(roomType.getName());
        booking.setCheckInDate(LocalDate.of(2027, 10, 1));
        booking.setCheckOutDate(LocalDate.of(2027, 10, 3));
        when(bookingRepository.findByIdForUpdate(151L)).thenReturn(Optional.of(booking));
        when(roomTypeRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(roomType));
        when(roomTypeRepository.findById(51L)).thenReturn(Optional.of(roomType));
        when(roomAvailabilityService.availableRooms(eq(roomType), any(), any(), eq(151L))).thenReturn(1);
        when(operatingSettingsService.findEffectiveAt(any())).thenReturn(new OperatingSettings());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookingService.updateBooking(151L,
                new BookingUpdateRequest(51L, LocalDate.of(2027, 10, 2), LocalDate.of(2027, 10, 4)));

        assertThat(booking.getRoom()).isNull();
        verify(roomAvailabilityService).assignRoom(any(), any(), any(), any(), any());
    }

    @Test
    void getBookingHistory_traVeDanhSachSapXepTheoCreatedAtGiamDan() {
        when(bookingRepository.existsById(300L)).thenReturn(true);

        Booking booking = new Booking();
        booking.setId(300L);

        BookingAuditLog log1 = new BookingAuditLog();
        log1.setId(10L);
        log1.setBooking(booking);
        log1.setBookingCode("BK-300");
        log1.setOldCheckInDate(LocalDate.of(2027, 1, 1));
        log1.setNewCheckInDate(LocalDate.of(2027, 1, 2));
        log1.setOldCheckOutDate(LocalDate.of(2027, 1, 3));
        log1.setNewCheckOutDate(LocalDate.of(2027, 1, 5));
        log1.setOldRoomTypeName("Phòng 1");
        log1.setNewRoomTypeName("Phòng 2");

        when(bookingAuditLogRepository.findByBookingIdOrderByCreatedAtDescIdDesc(300L))
                .thenReturn(List.of(log1));

        List<BookingAuditLogResponse> history = bookingService.getBookingHistory(300L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).bookingId()).isEqualTo(300L);
        assertThat(history.get(0).bookingCode()).isEqualTo("BK-300");
    }
}
