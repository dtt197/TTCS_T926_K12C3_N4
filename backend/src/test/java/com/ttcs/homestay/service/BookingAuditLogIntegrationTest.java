package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ttcs.homestay.dto.booking.BookingAuditLogResponse;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.BookingUpdateRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingAuditLog;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.RoomUnavailableException;
import com.ttcs.homestay.repository.BookingAuditLogRepository;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * SCRUM-96 (S3-04): "Hệ thống bảo đảm dữ liệu booking và lịch sử thay đổi được ghi nhận đầy đủ sau mỗi lần chỉnh sửa"
 */
@SpringBootTest
class BookingAuditLogIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingAuditLogRepository bookingAuditLogRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private OperatingSettingsRepository operatingSettingsRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Booking existingBooking;
    private RoomType roomTypeStandard;
    private RoomType roomTypeDeluxe;

    @BeforeEach
    void setUp() {
        bookingAuditLogRepository.deleteAll();
        bookingRepository.deleteAll();

        operatingSettingsRepository.findAll().stream().findFirst().orElseGet(() -> {
            OperatingSettings s = new OperatingSettings();
            s.setHomestayName("HomeStay Test");
            s.setCheckInTime(LocalTime.of(14, 0));
            s.setCheckOutTime(LocalTime.of(12, 0));
            s.setLateCheckoutFeePerHour(100_000L);
            s.setExtraPersonFee(200_000L);
            s.setCreatedByName("Hệ thống");
            s.setCreatedAt(OffsetDateTime.now().minusDays(1));
            s.setWeekendDays("FRIDAY,SATURDAY");
            return operatingSettingsRepository.save(s);
        });

        roomTypeStandard = saveRoomType("AUDIT_STD", "Phòng Tiêu Chuẩn", 500_000L, 700_000L);
        roomTypeDeluxe = saveRoomType("AUDIT_DLX", "Phòng Cao Cấp", 800_000L, 1_000_000L);

        // 3 đêm: T5 (500k), T6 (700k), T7 (700k) => 1.900.000đ
        existingBooking = new Booking();
        existingBooking.setBookingCode("BKAUDIT001");
        existingBooking.setGuestName("Nguyễn Văn Khách");
        existingBooking.setStatus(BookingStatus.DA_XAC_NHAN);
        existingBooking.setRoomType(roomTypeStandard);
        existingBooking.setRoomTypeNameSnapshot(roomTypeStandard.getName());
        existingBooking.setCheckInDate(LocalDate.of(2027, 6, 10));
        existingBooking.setCheckOutDate(LocalDate.of(2027, 6, 13));
        existingBooking.setWeekdayPriceSnapshot(500_000L);
        existingBooking.setWeekendPriceSnapshot(700_000L);
        existingBooking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        existingBooking.setTotalAmount(1_900_000L);
        existingBooking.setCreatedAt(OffsetDateTime.now());
        existingBooking = bookingRepository.saveAndFlush(existingBooking);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        bookingAuditLogRepository.deleteAll();
        bookingRepository.deleteAll();
    }

    private RoomType saveRoomType(String code, String name, Long weekdayPrice, Long weekendPrice) {
        RoomType roomType = roomTypeRepository.findAll().stream()
                .filter(rt -> code.equals(rt.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    RoomType rt = new RoomType();
                    rt.setCode(code);
                    rt.setName(name);
                    rt.setStandardCapacity(2);
                    rt.setMaxCapacity(3);
                    rt.setNumberOfBeds(1);
                    rt.setStatus(true);
                    rt.setWeekdayPrice(weekdayPrice);
                    rt.setWeekendPrice(weekendPrice);
                    return roomTypeRepository.save(rt);
                });

        roomRepository.findAll().stream()
                .filter(r -> ("R-" + code).equals(r.getRoomNumber()))
                .findFirst()
                .orElseGet(() -> {
                    Room r = new Room();
                    r.setRoomNumber("R-" + code);
                    r.setFloor(1);
                    r.setRoomType(name);
                    r.setStatus(RoomStatus.TRONG_SACH);
                    r.setActive(true);
                    return roomRepository.save(r);
                });

        return roomType;
    }

    @Test
    @DisplayName("1. Kiểm tra dữ liệu lịch sử được tạo đúng và đủ sau khi đổi ngày")
    void testAuditLogCreatedCorrectlyAfterDateChange() {
        Long bookingId = existingBooking.getId();
        OffsetDateTime beforeTime = OffsetDateTime.now().minusSeconds(1);

        BookingService.ActorInfo actor = new BookingService.ActorInfo(88L, "Lễ tân Mai", "mai.letan@homestay.local");

        // Đổi ngày sang: 2027-07-01 đến 2027-07-05 (4 đêm: T5 500k, T6 700k, T7 700k, CN 500k => 2.400.000đ)
        BookingResponse response = bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        roomTypeStandard.getId(),
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 5)),
                actor
        );

        assertThat(response.id()).isEqualTo(bookingId);

        // Kiểm tra audit log trong database
        List<BookingAuditLog> logs = bookingAuditLogRepository.findByBookingIdOrderByCreatedAtDescIdDesc(bookingId);
        assertThat(logs).hasSize(1);

        BookingAuditLog log = logs.get(0);
        // Kiểm tra ngày cũ và ngày mới
        assertThat(log.getOldCheckInDate()).isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(log.getNewCheckInDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(log.getOldCheckOutDate()).isEqualTo(LocalDate.of(2027, 6, 13));
        assertThat(log.getNewCheckOutDate()).isEqualTo(LocalDate.of(2027, 7, 5));

        // Kiểm tra loại phòng cũ và mới (giữ nguyên loại phòng)
        assertThat(log.getOldRoomTypeId()).isEqualTo(roomTypeStandard.getId());
        assertThat(log.getNewRoomTypeId()).isEqualTo(roomTypeStandard.getId());
        assertThat(log.getOldRoomTypeName()).isEqualTo(roomTypeStandard.getName());
        assertThat(log.getNewRoomTypeName()).isEqualTo(roomTypeStandard.getName());

        // Kiểm tra tổng tiền cũ và mới
        assertThat(log.getOldTotalAmount()).isEqualTo(1_900_000L);
        assertThat(log.getNewTotalAmount()).isEqualTo(2_400_000L);

        // Kiểm tra người thực hiện và thời điểm
        assertThat(log.getActorUserId()).isEqualTo(88L);
        assertThat(log.getActorName()).isEqualTo("Lễ tân Mai");
        assertThat(log.getActorEmail()).isEqualTo("mai.letan@homestay.local");
        assertThat(log.getCreatedAt()).isAfterOrEqualTo(beforeTime);
        assertThat(log.getCreatedAt()).isBeforeOrEqualTo(OffsetDateTime.now().plusSeconds(2));
    }

    @Test
    @DisplayName("2. Kiểm tra dữ liệu lịch sử được tạo đúng và đủ sau khi đổi loại phòng")
    void testAuditLogCreatedCorrectlyAfterRoomTypeChange() {
        Long bookingId = existingBooking.getId();

        BookingService.ActorInfo actor = new BookingService.ActorInfo(99L, "Lễ tân Tuấn", "tuan.letan@homestay.local");

        // Đổi loại phòng sang Deluxe, giữ nguyên ngày (3 đêm: 800k + 1m + 1m => 2.800.000đ)
        BookingResponse response = bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        roomTypeDeluxe.getId(),
                        existingBooking.getCheckInDate(),
                        existingBooking.getCheckOutDate()),
                actor
        );

        assertThat(response.roomTypeId()).isEqualTo(roomTypeDeluxe.getId());

        List<BookingAuditLog> logs = bookingAuditLogRepository.findByBookingIdOrderByCreatedAtDescIdDesc(bookingId);
        assertThat(logs).hasSize(1);

        BookingAuditLog log = logs.get(0);
        // Loại phòng thay đổi
        assertThat(log.getOldRoomTypeId()).isEqualTo(roomTypeStandard.getId());
        assertThat(log.getNewRoomTypeId()).isEqualTo(roomTypeDeluxe.getId());
        assertThat(log.getOldRoomTypeName()).isEqualTo(roomTypeStandard.getName());
        assertThat(log.getNewRoomTypeName()).isEqualTo(roomTypeDeluxe.getName());

        // Ngày ở giữ nguyên
        assertThat(log.getOldCheckInDate()).isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(log.getNewCheckInDate()).isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(log.getOldCheckOutDate()).isEqualTo(LocalDate.of(2027, 6, 13));
        assertThat(log.getNewCheckOutDate()).isEqualTo(LocalDate.of(2027, 6, 13));

        // Tổng tiền cập nhật tương ứng giá phòng mới
        assertThat(log.getOldTotalAmount()).isEqualTo(1_900_000L);
        assertThat(log.getNewTotalAmount()).isEqualTo(2_800_000L);

        // Thông tin người thao tác
        assertThat(log.getActorUserId()).isEqualTo(99L);
        assertThat(log.getActorName()).isEqualTo("Lễ tân Tuấn");
        assertThat(log.getActorEmail()).isEqualTo("tuan.letan@homestay.local");
    }

    @Test
    @DisplayName("3. Kiểm tra thông tin người thao tác (User) và thời điểm (Timestamp) được lưu chính xác")
    void testActorAndTimestampRecordedAccurately() {
        Long bookingId = existingBooking.getId();
        OffsetDateTime startTime = OffsetDateTime.now();

        BookingService.ActorInfo actor = new BookingService.ActorInfo(105L, "Quản lý Hùng", "hung.manager@homestay.local");

        bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        roomTypeStandard.getId(),
                        LocalDate.of(2027, 6, 11),
                        LocalDate.of(2027, 6, 14)),
                actor
        );

        List<BookingAuditLog> logs = bookingAuditLogRepository.findByBookingIdOrderByCreatedAtDescIdDesc(bookingId);
        assertThat(logs).isNotEmpty();

        BookingAuditLog log = logs.get(0);
        assertThat(log.getActorUserId()).isEqualTo(105L);
        assertThat(log.getActorName()).isEqualTo("Quản lý Hùng");
        assertThat(log.getActorEmail()).isEqualTo("hung.manager@homestay.local");
        assertThat(log.getCreatedAt()).isNotNull();
        assertThat(log.getCreatedAt()).isAfterOrEqualTo(startTime.minusSeconds(1));
    }

    @Test
    @DisplayName("4. Kiểm tra chính xác các giá trị cũ (old values) và mới (new values) qua nhiều lần chỉnh sửa liên tiếp")
    void testOldAndNewValuesPreservedSequentiallyAcrossMultipleEdits() {
        Long bookingId = existingBooking.getId();

        // Lần sửa 1: Đổi ngày
        BookingService.ActorInfo actor1 = new BookingService.ActorInfo(1L, "Lễ tân 1", "letan1@homestay.local");
        bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        roomTypeStandard.getId(),
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 5)),
                actor1
        );

        // Lần sửa 2: Đổi sang loại phòng Deluxe
        BookingService.ActorInfo actor2 = new BookingService.ActorInfo(2L, "Lễ tân 2", "letan2@homestay.local");
        bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        roomTypeDeluxe.getId(),
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 5)),
                actor2
        );

        // Lấy danh sách lịch sử sắp xếp mới nhất trước
        List<BookingAuditLog> logs = bookingAuditLogRepository.findByBookingIdOrderByCreatedAtDescIdDesc(bookingId);
        assertThat(logs).hasSize(2);

        BookingAuditLog secondEditLog = logs.get(0); // Mới nhất
        BookingAuditLog firstEditLog = logs.get(1);  // Lần đầu

        // Kiểm tra lần 1
        assertThat(firstEditLog.getOldCheckInDate()).isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(firstEditLog.getNewCheckInDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(firstEditLog.getOldTotalAmount()).isEqualTo(1_900_000L);
        assertThat(firstEditLog.getNewTotalAmount()).isEqualTo(2_400_000L);
        assertThat(firstEditLog.getActorName()).isEqualTo("Lễ tân 1");

        // Kiểm tra lần 2: old value của lần 2 khớp chính xác với new value của lần 1
        assertThat(secondEditLog.getOldCheckInDate()).isEqualTo(firstEditLog.getNewCheckInDate());
        assertThat(secondEditLog.getOldCheckOutDate()).isEqualTo(firstEditLog.getNewCheckOutDate());
        assertThat(secondEditLog.getOldRoomTypeId()).isEqualTo(firstEditLog.getNewRoomTypeId());
        assertThat(secondEditLog.getOldTotalAmount()).isEqualTo(firstEditLog.getNewTotalAmount());

        // new value của lần 2
        assertThat(secondEditLog.getNewRoomTypeId()).isEqualTo(roomTypeDeluxe.getId());
        assertThat(secondEditLog.getNewRoomTypeName()).isEqualTo(roomTypeDeluxe.getName());
        assertThat(secondEditLog.getActorName()).isEqualTo("Lễ tân 2");
    }

    @Test
    @DisplayName("5. Kiểm tra lỗi xảy ra trong quá trình cập nhật -> Không lưu dở dang (Validation Failure)")
    void testExceptionDuringUpdateDoesNotPersistAnyChangesOrAuditLog() {
        Long bookingId = existingBooking.getId();

        // Thử cập nhật ngày không hợp lệ (check-in sau check-out)
        assertThatThrownBy(() -> bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        roomTypeDeluxe.getId(),
                        LocalDate.of(2027, 7, 10),
                        LocalDate.of(2027, 7, 5))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Booking không thay đổi
        Booking bookingAfter = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(bookingAfter.getCheckInDate()).isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(bookingAfter.getRoomType().getId()).isEqualTo(roomTypeStandard.getId());
        assertThat(bookingAfter.getTotalAmount()).isEqualTo(1_900_000L);

        // Không có audit log nào được tạo
        assertThat(bookingAuditLogRepository.countByBookingId(bookingId)).isZero();
    }

    @Test
    @DisplayName("6. Kiểm tra Database Transaction Rollback -> Không lưu dở dang booking và log khi transaction bị rollback")
    void testTransactionRollbackEnsuresAtomicityAndLeavesNoPartialState() {
        Long bookingId = existingBooking.getId();

        // Chạy trong transaction và chủ động rollback
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            bookingService.updateBooking(
                    bookingId,
                    new BookingUpdateRequest(
                            roomTypeDeluxe.getId(),
                            LocalDate.of(2027, 8, 1),
                            LocalDate.of(2027, 8, 5))
            );

            // Kiểm tra trong phạm vi transaction thì booking và log đã được cập nhật
            assertThat(bookingRepository.findById(bookingId).orElseThrow().getCheckInDate())
                    .isEqualTo(LocalDate.of(2027, 8, 1));
            assertThat(bookingAuditLogRepository.countByBookingId(bookingId)).isEqualTo(1);

            // Gặp lỗi / yêu cầu Rollback
            status.setRollbackOnly();
        });

        // Sau khi Transaction Rollback kết thúc: kiểm tra database ngoài transaction
        Booking restoredBooking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(restoredBooking.getCheckInDate()).isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(restoredBooking.getCheckOutDate()).isEqualTo(LocalDate.of(2027, 6, 13));
        assertThat(restoredBooking.getRoomType().getId()).isEqualTo(roomTypeStandard.getId());
        assertThat(restoredBooking.getTotalAmount()).isEqualTo(1_900_000L);

        // Audit log không bị lưu dở dang
        assertThat(bookingAuditLogRepository.countByBookingId(bookingId)).isZero();
    }

    @Test
    @DisplayName("7. Kiểm tra API / Service getBookingHistory trả về đầy đủ lịch sử")
    void testGetBookingHistoryReturnsFullHistoryChronologically() {
        Long bookingId = existingBooking.getId();

        bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        roomTypeStandard.getId(),
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 5))
        );

        List<BookingAuditLogResponse> history = bookingService.getBookingHistory(bookingId);
        assertThat(history).hasSize(1);

        BookingAuditLogResponse item = history.get(0);
        assertThat(item.bookingId()).isEqualTo(bookingId);
        assertThat(item.bookingCode()).isEqualTo("BKAUDIT001");
        assertThat(item.oldCheckInDate()).isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(item.newCheckInDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(item.oldTotalAmount()).isEqualTo(1_900_000L);
        assertThat(item.newTotalAmount()).isEqualTo(2_400_000L);
    }
}
