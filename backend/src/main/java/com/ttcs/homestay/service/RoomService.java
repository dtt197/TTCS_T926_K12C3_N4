package com.ttcs.homestay.service;
import com.ttcs.homestay.dto.RoomStatusHistoryResponse;
import com.ttcs.homestay.dto.CheckInRequest;
import com.ttcs.homestay.dto.CheckInResponse;
import com.ttcs.homestay.dto.MaintenanceRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.entity.CheckIn;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomStatusHistory;
import com.ttcs.homestay.exception.RoomNotFoundException;
import com.ttcs.homestay.exception.RoomStatusConflictException;
import com.ttcs.homestay.repository.CheckInRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RoomRepository roomRepository;
    private final CheckInRepository checkInRepository;
    private final RoomStatusHistoryRepository roomStatusHistoryRepository;

    public RoomService(
            RoomRepository roomRepository,
            CheckInRepository checkInRepository,
            RoomStatusHistoryRepository roomStatusHistoryRepository) {
        this.roomRepository = roomRepository;
        this.checkInRepository = checkInRepository;
        this.roomStatusHistoryRepository = roomStatusHistoryRepository;
    }
        @Transactional(readOnly = true)
    public List<RoomResponse> getRooms() {
        return roomRepository.findAllByActiveTrueOrderByRoomNumberAsc()
                .stream()
                .map(RoomResponse::from)
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
                    "Khoảng ngày bảo trì không hợp lệ. Ngày kết thúc phải từ ngày bắt đầu trở đi.");
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

    @Transactional
    public CheckInResponse checkIn(Long roomId, CheckInRequest request, String operatorName) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (!RoomStatusPolicy.canCheckIn(room.getStatus())) {
            throw new RoomStatusConflictException(
                    "Không thể nhận phòng " + room.getRoomNumber() + " vì phòng đang ở trạng thái "
                            + RoomStatusPolicy.displayName(room.getStatus())
                            + ". Chỉ phòng Trống sạch mới được gán khi nhận phòng.");
        }

        RoomStatus previousStatus = room.getStatus();

        room.setStatus(RoomStatus.DANG_O);
        Room savedRoom = roomRepository.save(room);

        CheckIn checkIn = new CheckIn();
        checkIn.setRoom(savedRoom);
        checkIn.setGuestName(request.guestName().trim());
        checkIn.setCheckedInAt(OffsetDateTime.now(BUSINESS_ZONE));
        CheckInResponse response = CheckInResponse.from(checkInRepository.save(checkIn));

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