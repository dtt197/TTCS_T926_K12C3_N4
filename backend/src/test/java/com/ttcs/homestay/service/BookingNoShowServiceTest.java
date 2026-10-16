package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.AppointmentActionLogResponse;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.CustomerNoShowResponse;
import com.ttcs.homestay.dto.booking.NoShowEligibilityResponse;
import com.ttcs.homestay.dto.booking.NoShowUndoEligibilityResponse;
import com.ttcs.homestay.entity.AppointmentActionLog;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Customer;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.AppointmentActionLogRepository;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.CustomerRepository;
import com.ttcs.homestay.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class BookingNoShowServiceTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final AppointmentActionLogRepository actionLogRepository = mock(AppointmentActionLogRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final OperatingSettingsService settingsService = mock(OperatingSettingsService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private BookingNoShowService service;

    @BeforeEach
    void setUp() {
        service = new BookingNoShowService(
                bookingRepository,
                customerRepository,
                actionLogRepository,
                userRepository,
                settingsService,
                auditLogService
        );

        OperatingSettings settings = new OperatingSettings();
        settings.setCheckInTime(LocalTime.of(14, 0));
        when(settingsService.findEffectiveAt(any())).thenReturn(settings);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Booking createBooking(Long id, BookingStatus status, LocalDate checkInDate) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setBookingCode("BK-" + id);
        booking.setGuestName("Nguyen Van A");
        booking.setGuestPhone("0987654321");
        booking.setGuestEmail("vana@example.com");
        booking.setStatus(status);
        booking.setCheckInDate(checkInDate);
        booking.setCheckOutDate(checkInDate.plusDays(1));
        booking.setCreatedAt(OffsetDateTime.now(BookingNoShowService.ZONE_VN).minusDays(1));
        booking.setRoomTypeNameSnapshot("Deluxe Room");

        RoomType roomType = new RoomType();
        roomType.setName("Deluxe Room");
        roomType.setCheckInTime(LocalTime.of(14, 0));
        booking.setRoomType(roomType);

        Room room = new Room();
        room.setId(101L);
        room.setRoomNumber("101");
        booking.setRoom(room);

        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(id)).thenReturn(Optional.of(booking));
        return booking;
    }

    @Test
    @DisplayName("Kịch bản 1: Bấm nút trước giờ hẹn 30 phút -> Bị khóa / Báo lỗi")
    void markNoShow_before30Minutes_fails() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        createBooking(1L, BookingStatus.DA_XAC_NHAN, today);

        // Giờ check-in 14:00. Lúc 14:15 mới trôi qua 15 phút, chưa đủ 30 phút.
        OffsetDateTime evaluationTime = today.atTime(14, 15).atZone(BookingNoShowService.ZONE_VN).toOffsetDateTime();

        // 1. Kiểm tra API eligibility
        NoShowEligibilityResponse eligibility = service.checkEligibility(1L, evaluationTime);
        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).contains("Chưa đủ điều kiện");

        // 2. Thao tác markNoShow bị ném ngoại lệ 400 Bad Request
        assertThatThrownBy(() -> service.markNoShow(1L, evaluationTime, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Chưa đủ điều kiện");
    }

    @Test
    @DisplayName("Kịch bản 2: Bấm nút sau giờ hẹn 30 phút khi đã check-in -> Bị khóa / Báo lỗi")
    void markNoShow_after30Minutes_alreadyCheckedIn_fails() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        createBooking(2L, BookingStatus.DA_NHAN_PHONG, today);

        // Lúc 15:00 đã quá 30 phút sau 14:00 nhưng khách đã check-in
        OffsetDateTime evaluationTime = today.atTime(15, 0).atZone(BookingNoShowService.ZONE_VN).toOffsetDateTime();

        // 1. Kiểm tra API eligibility
        NoShowEligibilityResponse eligibility = service.checkEligibility(2L, evaluationTime);
        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.checkedIn()).isTrue();
        assertThat(eligibility.reason()).contains("đã check-in");

        // 2. Thao tác markNoShow bị ném ngoại lệ 409 Conflict
        assertThatThrownBy(() -> service.markNoShow(2L, evaluationTime, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đã check-in");
    }

    @Test
    @DisplayName("Kịch bản 3: Bấm nút sau giờ hẹn 30 phút khi chưa check-in -> Thành công, giải phóng lịch phòng")
    void markNoShow_after30Minutes_notCheckedIn_success() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        Booking booking = createBooking(3L, BookingStatus.DA_XAC_NHAN, today);

        // Giả lập khách hàng cũ đã có trong cơ sở dữ liệu
        Customer existingCustomer = new Customer("Nguyen Van A", "0987654321", "vana@example.com");
        existingCustomer.setNoShowCount(2);
        when(customerRepository.findFirstByPhone("0987654321")).thenReturn(Optional.of(existingCustomer));

        // Lúc 14:35 đã quá 30 phút kể từ 14:00 và khách chưa nhận phòng
        OffsetDateTime evaluationTime = today.atTime(14, 35).atZone(BookingNoShowService.ZONE_VN).toOffsetDateTime();

        // 1. Kiểm tra eligibility
        NoShowEligibilityResponse eligibility = service.checkEligibility(3L, evaluationTime);
        assertThat(eligibility.eligible()).isTrue();
        assertThat(eligibility.reason()).contains("Đủ điều kiện");

        // 2. Thực hiện đánh dấu không đến
        BookingResponse response = service.markNoShow(3L, evaluationTime, "127.0.0.1");

        // Kiểm tra trạng thái booking đã cập nhật sang NO_SHOW
        assertThat(response.status()).isEqualTo(BookingStatus.NO_SHOW);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.NO_SHOW);

        // Kiểm tra tăng no_show_count cho khách hàng (từ 2 lên 3)
        assertThat(existingCustomer.getNoShowCount()).isEqualTo(3);
        verify(customerRepository).save(existingCustomer);

        // Kiểm tra lưu bản ghi vào appointment_action_logs
        ArgumentCaptor<AppointmentActionLog> logCaptor = ArgumentCaptor.forClass(AppointmentActionLog.class);
        verify(actionLogRepository).save(logCaptor.capture());
        AppointmentActionLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getAppointmentId()).isEqualTo(3L);
        assertThat(savedLog.getActionType()).isEqualTo("MARK_NO_SHOW");
        assertThat(savedLog.getPreviousStatus()).isEqualTo("DA_XAC_NHAN");

        // Kiểm tra ghi audit log
        verify(auditLogService).recordSensitiveAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Kịch bản 3b: Khách hàng chưa có trong bảng customers thì tự động tạo mới với no_show_count = 1")
    void markNoShow_newCustomer_createsCustomerWithCountOne() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        createBooking(4L, BookingStatus.CHO_XAC_NHAN, today);

        when(customerRepository.findFirstByPhone("0987654321")).thenReturn(Optional.empty());
        when(customerRepository.findFirstByEmail("vana@example.com")).thenReturn(Optional.empty());

        OffsetDateTime evaluationTime = today.atTime(15, 0).atZone(BookingNoShowService.ZONE_VN).toOffsetDateTime();
        BookingResponse response = service.markNoShow(4L, evaluationTime, "127.0.0.1");

        assertThat(response.status()).isEqualTo(BookingStatus.NO_SHOW);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer createdCustomer = customerCaptor.getValue();
        assertThat(createdCustomer.getPhone()).isEqualTo("0987654321");
        assertThat(createdCustomer.getNoShowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("S4-04 Lát 2: Đánh dấu không đến cho khách A (SĐT X) -> no_show_count tăng thêm 1")
    void markNoShow_existingCustomer_incrementsCountByOne() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        Booking booking = createBooking(5L, BookingStatus.DA_XAC_NHAN, today);
        booking.setGuestPhone("0912345678");

        Customer existingCustomer = new Customer("Khach Hang A", "0912345678", "khacha@example.com");
        existingCustomer.setNoShowCount(1);
        when(customerRepository.findFirstByPhone("0912345678")).thenReturn(Optional.of(existingCustomer));

        OffsetDateTime evaluationTime = today.atTime(15, 0).atZone(BookingNoShowService.ZONE_VN).toOffsetDateTime();
        BookingResponse response = service.markNoShow(5L, evaluationTime, "127.0.0.1");

        assertThat(response.status()).isEqualTo(BookingStatus.NO_SHOW);
        assertThat(existingCustomer.getNoShowCount()).isEqualTo(2);
        verify(customerRepository).save(existingCustomer);
    }

    @Test
    @DisplayName("S4-04 Lát 2: Tra cứu số lần không đến theo SĐT -> Khách có lịch sử bỏ hẹn hiển thị cảnh báo")
    void getCustomerNoShowHistory_existingWithNoShows_returnsWarning() {
        Customer customer = new Customer("Nguyen Van A", "0987654321", "vana@example.com");
        customer.setNoShowCount(2);
        when(customerRepository.findFirstByPhone("0987654321")).thenReturn(Optional.of(customer));

        CustomerNoShowResponse response = service.getCustomerNoShowHistory(" 0987654321 ");
        assertThat(response.phone()).isEqualTo("0987654321");
        assertThat(response.fullName()).isEqualTo("Nguyen Van A");
        assertThat(response.noShowCount()).isEqualTo(2);
        assertThat(response.hasWarning()).isTrue();
        assertThat(response.warningMessage()).contains("⚠️ Khách hàng này đã bỏ hẹn 2 lần");
    }

    @Test
    @DisplayName("S4-04 Lát 2: Tra cứu số lần không đến theo SĐT -> Khách mới hoặc không có bỏ hẹn không cảnh báo")
    void getCustomerNoShowHistory_cleanCustomerOrNotFound_returnsNoWarning() {
        // Trường hợp 1: Không tìm thấy khách theo SĐT
        when(customerRepository.findFirstByPhone("0900000000")).thenReturn(Optional.empty());
        CustomerNoShowResponse notFound = service.getCustomerNoShowHistory("0900000000");
        assertThat(notFound.noShowCount()).isEqualTo(0);
        assertThat(notFound.hasWarning()).isFalse();
        assertThat(notFound.warningMessage()).isNull();

        // Trường hợp 2: Khách có hồ sơ nhưng no_show_count = 0
        Customer cleanCustomer = new Customer("Tran Van C", "0911111111", "vanc@example.com");
        cleanCustomer.setNoShowCount(0);
        when(customerRepository.findFirstByPhone("0911111111")).thenReturn(Optional.of(cleanCustomer));
        CustomerNoShowResponse cleanResponse = service.getCustomerNoShowHistory("0911111111");
        assertThat(cleanResponse.noShowCount()).isEqualTo(0);
        assertThat(cleanResponse.hasWarning()).isFalse();
        assertThat(cleanResponse.warningMessage()).isNull();

        // Trường hợp 3: SĐT null hoặc rỗng
        CustomerNoShowResponse emptyResponse = service.getCustomerNoShowHistory("");
        assertThat(emptyResponse.noShowCount()).isEqualTo(0);
        assertThat(emptyResponse.hasWarning()).isFalse();
    }

    @Test
    @DisplayName("S4-04 Lát 3: Hoàn tác trong vòng 15 phút -> Khôi phục trạng thái thành công, no_show_count giảm 1, ghi log UNDO_NO_SHOW")
    void undoNoShow_within15Minutes_restoresStatusAndDecrementsCustomerNoShowCount() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        Booking booking = createBooking(6L, BookingStatus.NO_SHOW, today);

        Customer customer = new Customer("Nguyen Van A", "0987654321", "vana@example.com");
        customer.setNoShowCount(2);
        when(customerRepository.findFirstByPhone("0987654321")).thenReturn(Optional.of(customer));

        OffsetDateTime evaluationTime = today.atTime(14, 40).atZone(BookingNoShowService.ZONE_VN).toOffsetDateTime();
        AppointmentActionLog markLog = new AppointmentActionLog(6L, 101L, "MARK_NO_SHOW", "DA_XAC_NHAN");
        markLog.setCreatedAt(evaluationTime.minusMinutes(5));
        when(actionLogRepository.findByAppointmentIdOrderByCreatedAtDesc(6L)).thenReturn(List.of(markLog));

        BookingResponse response = service.undoNoShow(6L, evaluationTime, "127.0.0.1");

        assertThat(response.status()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        assertThat(customer.getNoShowCount()).isEqualTo(1);
        verify(customerRepository).save(customer);

        ArgumentCaptor<AppointmentActionLog> logCaptor = ArgumentCaptor.forClass(AppointmentActionLog.class);
        verify(actionLogRepository).save(logCaptor.capture());
        AppointmentActionLog undoLog = logCaptor.getValue();
        assertThat(undoLog.getActionType()).isEqualTo("UNDO_NO_SHOW");
        assertThat(undoLog.getAppointmentId()).isEqualTo(6L);
        assertThat(undoLog.getPreviousStatus()).isEqualTo("NO_SHOW");

        verify(auditLogService).recordSensitiveAction(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq("BOOKING_UNDO_NO_SHOW"), any());
    }

    @Test
    @DisplayName("S4-04 Lát 3: Hoàn tác ở phút thứ 16 -> Báo lỗi quá thời hạn 15 phút")
    void undoNoShow_atMinute16_throwsBadRequest() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        createBooking(7L, BookingStatus.NO_SHOW, today);

        OffsetDateTime evaluationTime = today.atTime(14, 56).atZone(BookingNoShowService.ZONE_VN).toOffsetDateTime();
        AppointmentActionLog markLog = new AppointmentActionLog(7L, 101L, "MARK_NO_SHOW", "DA_XAC_NHAN");
        markLog.setCreatedAt(evaluationTime.minusMinutes(16));
        when(actionLogRepository.findByAppointmentIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(markLog));

        assertThatThrownBy(() -> service.undoNoShow(7L, evaluationTime, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Đã quá thời hạn 15 phút");
    }

    @Test
    @DisplayName("S4-04 Lát 3: Kiểm tra quyền hoàn tác trong vòng 15 phút -> Eligible = true kèm số giây còn lại")
    void checkUndoEligibility_within15Minutes_returnsEligibleAndRemainingSeconds() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        createBooking(8L, BookingStatus.NO_SHOW, today);

        User receptionist = mock(User.class);
        when(receptionist.getFullName()).thenReturn("Lễ tân Hoa");
        when(userRepository.findById(101L)).thenReturn(Optional.of(receptionist));

        OffsetDateTime evaluationTime = today.atTime(14, 40).atZone(BookingNoShowService.ZONE_VN).toOffsetDateTime();
        AppointmentActionLog markLog = new AppointmentActionLog(8L, 101L, "MARK_NO_SHOW", "DA_XAC_NHAN");
        markLog.setCreatedAt(evaluationTime.minusMinutes(5));
        when(actionLogRepository.findByAppointmentIdOrderByCreatedAtDesc(8L)).thenReturn(List.of(markLog));

        NoShowUndoEligibilityResponse eligibility = service.checkUndoEligibility(8L, evaluationTime);

        assertThat(eligibility.eligible()).isTrue();
        assertThat(eligibility.remainingSeconds()).isEqualTo(10 * 60L);
        assertThat(eligibility.receptionistId()).isEqualTo(101L);
        assertThat(eligibility.receptionistName()).isEqualTo("Lễ tân Hoa");
    }

    @Test
    @DisplayName("S4-04 Lát 3: Kiểm tra quyền hoàn tác sau 15 phút -> Eligible = false và remainingSeconds = 0")
    void checkUndoEligibility_after15Minutes_returnsNotEligible() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        createBooking(9L, BookingStatus.NO_SHOW, today);

        OffsetDateTime evaluationTime = today.atTime(15, 0).atZone(BookingNoShowService.ZONE_VN).toOffsetDateTime();
        AppointmentActionLog markLog = new AppointmentActionLog(9L, 101L, "MARK_NO_SHOW", "DA_XAC_NHAN");
        markLog.setCreatedAt(evaluationTime.minusMinutes(20));
        when(actionLogRepository.findByAppointmentIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(markLog));

        NoShowUndoEligibilityResponse eligibility = service.checkUndoEligibility(9L, evaluationTime);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.remainingSeconds()).isEqualTo(0);
        assertThat(eligibility.reason()).contains("Đã quá thời hạn 15 phút");
    }

    @Test
    @DisplayName("S4-04 Lát 3: Kiểm tra nhật ký hệ thống ghi đúng ID và tên Lễ tân thực hiện thao tác")
    void getActionLogs_returnsLogsWithReceptionistDetails() {
        User receptionist = mock(User.class);
        when(receptionist.getFullName()).thenReturn("Lễ tân Mai");
        when(userRepository.findById(205L)).thenReturn(Optional.of(receptionist));

        AppointmentActionLog log1 = new AppointmentActionLog(10L, 205L, "MARK_NO_SHOW", "DA_XAC_NHAN");
        log1.setId(1L);
        log1.setCreatedAt(OffsetDateTime.now(BookingNoShowService.ZONE_VN));

        AppointmentActionLog log2 = new AppointmentActionLog(10L, 205L, "UNDO_NO_SHOW", "NO_SHOW");
        log2.setId(2L);
        log2.setCreatedAt(OffsetDateTime.now(BookingNoShowService.ZONE_VN).plusMinutes(5));

        when(actionLogRepository.findByAppointmentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(log2, log1));

        List<AppointmentActionLogResponse> logs = service.getActionLogs(10L);

        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).actionType()).isEqualTo("UNDO_NO_SHOW");
        assertThat(logs.get(0).receptionistId()).isEqualTo(205L);
        assertThat(logs.get(0).receptionistName()).isEqualTo("Lễ tân Mai");

        assertThat(logs.get(1).actionType()).isEqualTo("MARK_NO_SHOW");
        assertThat(logs.get(1).receptionistId()).isEqualTo(205L);
        assertThat(logs.get(1).receptionistName()).isEqualTo("Lễ tân Mai");
    }
}
