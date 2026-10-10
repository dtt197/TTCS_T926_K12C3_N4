package com.ttcs.homestay.service;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttcs.homestay.dto.CheckInRequest;
import com.ttcs.homestay.dto.CheckInResponse;
import com.ttcs.homestay.dto.IncidentReportRequest;
import com.ttcs.homestay.dto.MaintenanceRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.dto.RoomStatusHistoryResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.CheckIn;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomStatusHistory;
import com.ttcs.homestay.exception.RoomNotFoundException;
import com.ttcs.homestay.exception.RoomStatusConflictException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.CheckInRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;

@Service
public class RoomService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final RoomRepository roomRepository;
    private final CheckInRepository checkInRepository;
    private final RoomStatusHistoryRepository roomStatusHistoryRepository;
    private final BookingRepository bookingRepository;
    private final OperatingSettingsRepository operatingSettingsRepository;

    public RoomService(
            RoomRepository roomRepository,
            CheckInRepository checkInRepository,
            RoomStatusHistoryRepository roomStatusHistoryRepository) {
        this(roomRepository, checkInRepository, roomStatusHistoryRepository, null, null);
    }

    @Autowired
    public RoomService(
            RoomRepository roomRepository,
            CheckInRepository checkInRepository,
            RoomStatusHistoryRepository roomStatusHistoryRepository,
            BookingRepository bookingRepository,
            OperatingSettingsRepository operatingSettingsRepository) {
        this.roomRepository = roomRepository;
        this.checkInRepository = checkInRepository;
        this.roomStatusHistoryRepository = roomStatusHistoryRepository;
        this.bookingRepository = bookingRepository;
        this.operatingSettingsRepository = operatingSettingsRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRooms() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        List<Booking> todayBookings = (bookingRepository != null)
                ? bookingRepository.findByCheckInDateAndStatusIn(
                        today,
                        List.of(BookingStatus.DA_XAC_NHAN, BookingStatus.CHO_XAC_NHAN))
                : List.of();

        LocalTime defaultCheckIn = LocalTime.of(14, 0);
        if (operatingSettingsRepository != null) {
            defaultCheckIn = operatingSettingsRepository.findFirstByOrderByCreatedAtDescIdDesc()
                    .map(settings -> settings.getCheckInTime())
                    .orElse(LocalTime.of(14, 0));
        }

        final LocalTime effectiveDefaultCheckIn = defaultCheckIn;

        return roomRepository.findAllByActiveTrueOrderByRoomNumberAsc()
                .stream()
                .map(room -> {
                    // S3-09: Tìm booking nhận phòng trong ngày tương ứng với loại phòng
                    Booking matchingBooking = todayBookings.stream()
                            .filter(booking -> {
                                String bookingRt = booking.getRoomType() != null
                                        ? booking.getRoomType().getName()
                                        : booking.getRoomTypeNameSnapshot();
                                return bookingRt != null && bookingRt.equalsIgnoreCase(room.getRoomType());
                            })
                            .findFirst()
                            .orElse(null);

                    boolean hasGuestToday = matchingBooking != null;
                    String expectedCheckInTimeStr = null;
                    if (matchingBooking != null) {
                        LocalTime roomCheckInTime = (matchingBooking.getRoomType() != null && matchingBooking.getRoomType().getCheckInTime() != null)
                                ? matchingBooking.getRoomType().getCheckInTime()
                                : effectiveDefaultCheckIn;
                        expectedCheckInTimeStr = (roomCheckInTime != null)
                                ? roomCheckInTime.format(TIME_FORMATTER)
                                : "14:00";
                    }

                    return RoomResponse.from(room, hasGuestToday, expectedCheckInTimeStr);
                })
                .toList();
    }
    @Transactional(readOnly = true)
public List<RoomStatusHistoryResponse> getHistory(
        Long roomId
) {
    if (!roomRepository.existsById(roomId)) {
        throw new RoomNotFoundException(roomId);
    }

    return roomStatusHistoryRepository
            .findByRoomIdInChronologicalOrder(roomId)
            .stream()
            .map(RoomStatusHistoryResponse::from)
            .toList();
}

    @Transactional
    public RoomResponse updateStatus(Long roomId, RoomStatus targetStatus, String operatorName) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (targetStatus == RoomStatus.BAO_TRI) {
            throw new RoomStatusConflictException(
                    "Vui lòng dùng biểu mẫu bảo trì để nhập lý do và khoảng ngày.");
        }

        if (!RoomStatusPolicy.canChangeTo(room.getStatus(), targetStatus)) {
            throw new RoomStatusConflictException(
                    "Không thể chuyển phòng " + room.getRoomNumber()
                            + " từ Đang ở sang Bảo trì. Hãy trả phòng trước.");
        }

        RoomStatus previousStatus = room.getStatus();

        room.setStatus(targetStatus);
        room.setMaintenanceReason(null);
        room.setMaintenanceStartDate(null);
        room.setMaintenanceEndDate(null);

        Room savedRoom = roomRepository.save(room);

        saveHistory(
                savedRoom,
                previousStatus,
                targetStatus,
                operatorName,
                null,
                null,
                null);

        return RoomResponse.from(savedRoom);
    }

    @Transactional
    public RoomResponse putIntoMaintenance(Long roomId, MaintenanceRequest request, String operatorName) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (room.getStatus() == RoomStatus.DANG_O) {
            throw new RoomStatusConflictException(
                    "Không thể đưa phòng " + room.getRoomNumber()
                            + " đang ở vào bảo trì. Hãy trả phòng trước.");
        }
        if (!RoomStatusPolicy.hasValidMaintenancePeriod(request.startDate(), request.endDate())) {
            throw new RoomStatusConflictException(
                    "Khoảng ngày bảo trì không hợp lệ. Vui lòng nhập ngày kết thúc dự kiến, từ ngày bắt đầu trở đi.");
        }

        RoomStatus previousStatus = room.getStatus();

        room.setStatus(RoomStatus.BAO_TRI);
        room.setMaintenanceReason(request.reason().trim());
        room.setMaintenanceStartDate(request.startDate());
        room.setMaintenanceEndDate(request.endDate());

        Room savedRoom = roomRepository.save(room);

        saveHistory(
                savedRoom,
                previousStatus,
                RoomStatus.BAO_TRI,
                operatorName,
                savedRoom.getMaintenanceReason(),
                savedRoom.getMaintenanceStartDate(),
                savedRoom.getMaintenanceEndDate());

        return RoomResponse.from(savedRoom);
    }

    /**
     * S3-09 AC4: Buồng phòng báo sự cố cho phòng TRONG_BAN.
     * Ghi chú không được rỗng; phòng chuyển sang BAO_TRI ngay lập tức để lễ tân thấy.
     */
    @Transactional
    public RoomResponse reportIncident(Long roomId, IncidentReportRequest request, String operatorName) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (room.getStatus() != RoomStatus.TRONG_BAN) {
            throw new RoomStatusConflictException(
                    "Chỉ có thể báo sự cố cho phòng đang ở trạng thái Trống bẩn. "
                            + "Phòng " + room.getRoomNumber() + " hiện đang ở trạng thái "
                            + RoomStatusPolicy.displayName(room.getStatus()) + ".");
        }

        RoomStatus previousStatus = room.getStatus();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        room.setStatus(RoomStatus.BAO_TRI);
        room.setMaintenanceReason(request.note().trim());
        room.setMaintenanceStartDate(today);
        room.setMaintenanceEndDate(null);

        Room savedRoom = roomRepository.save(room);

        saveHistory(
                savedRoom,
                previousStatus,
                RoomStatus.BAO_TRI,
                operatorName,
                savedRoom.getMaintenanceReason(),
                savedRoom.getMaintenanceStartDate(),
                null);

        return RoomResponse.from(savedRoom);
    }

    @Transactional
    public CheckInResponse checkIn(Long roomId, CheckInRequest request, String operatorName) {
        if (bookingRepository == null) {
            throw new IllegalStateException("BookingRepository is required for booking check-in");
        }
        Booking bookingSnapshot = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new RoomStatusConflictException("Không tìm thấy booking cần nhận phòng"));
        if (bookingSnapshot.getRoom() == null || !bookingSnapshot.getRoom().getId().equals(roomId)) {
            throw new RoomStatusConflictException("Booking chưa được gán vào phòng đã chọn");
        }

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));
        Booking booking = bookingRepository.findByIdForUpdate(request.bookingId())
                .orElseThrow(() -> new RoomStatusConflictException("Không tìm thấy booking cần nhận phòng"));

        if (booking.getStatus() != BookingStatus.DA_XAC_NHAN) {
            throw new RoomStatusConflictException("Chỉ booking đã xác nhận mới được nhận phòng");
        }
        if (booking.getRoom() == null || !booking.getRoom().getId().equals(roomId)
                || booking.getRoomConfirmedAt() == null) {
            throw new RoomStatusConflictException("Booking chưa được chốt phòng đã chọn");
        }

        if (!RoomStatusPolicy.canCheckIn(room.getStatus())) {
            throw new RoomStatusConflictException(
                    "Không thể nhận phòng " + room.getRoomNumber() + " vì phòng đang ở trạng thái "
                            + RoomStatusPolicy.displayName(room.getStatus())
                            + ". Chỉ phòng Trống sạch mới được gán khi nhận phòng.");
        }

        OffsetDateTime checkedInAt = OffsetDateTime.now(BUSINESS_ZONE);
        booking.setStatus(BookingStatus.DA_NHAN_PHONG);
        bookingRepository.save(booking);

        RoomStatus previousStatus = room.getStatus();

        room.setStatus(RoomStatus.DANG_O);
        Room savedRoom = roomRepository.save(room);

        CheckIn checkIn = new CheckIn();
        checkIn.setRoom(savedRoom);
        checkIn.setGuestName(booking.getGuestName());
        checkIn.setCheckedInAt(checkedInAt);
        CheckInResponse response = CheckInResponse.from(checkInRepository.save(checkIn), booking);

        saveHistory(
                savedRoom,
                previousStatus,
                RoomStatus.DANG_O,
                operatorName,
                null,
                null,
                null);

        return response;
    }

    @Transactional
    public RoomResponse checkOut(Long roomId, String operatorName) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (room.getStatus() != RoomStatus.DANG_O) {
            throw new RoomStatusConflictException(
                    "Không thể trả phòng " + room.getRoomNumber()
                            + " vì phòng không ở trạng thái Đang ở.");
        }

        RoomStatus previousStatus = room.getStatus();

        room.setStatus(RoomStatus.TRONG_BAN);
        room.setMaintenanceReason(null);
        room.setMaintenanceStartDate(null);
        room.setMaintenanceEndDate(null);

        Room savedRoom = roomRepository.save(room);

        saveHistory(
                savedRoom,
                previousStatus,
                RoomStatus.TRONG_BAN,
                operatorName,
                null,
                null,
                null);

        return RoomResponse.from(savedRoom);
    }

    private void saveHistory(
            Room room,
            RoomStatus previousStatus,
            RoomStatus newStatus,
            String operatorName,
            String maintenanceReason,
            LocalDate maintenanceStartDate,
            LocalDate maintenanceEndDate) {
        RoomStatusHistory history = new RoomStatusHistory();

        history.setRoom(room);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);

        if (operatorName == null || operatorName.isBlank()) {
            history.setChangedBy("Lễ tân");
        } else {
            history.setChangedBy(operatorName.trim());
        }

        history.setChangedAt(
                OffsetDateTime.now(BUSINESS_ZONE)
        );

        history.setMaintenanceReason(maintenanceReason);
        history.setMaintenanceStartDate(maintenanceStartDate);
        history.setMaintenanceEndDate(maintenanceEndDate);

        roomStatusHistoryRepository.save(history);
    }

    
}
