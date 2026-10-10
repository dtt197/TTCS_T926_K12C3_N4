package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.CreatePhysicalRoomRequest;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.dto.RoomSearchResponse;
import com.ttcs.homestay.dto.RoomUpdateResponse;
import com.ttcs.homestay.dto.UpdatePhysicalRoomRequest;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomNote;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.repository.RoomManagementRepository;
import com.ttcs.homestay.repository.RoomNoteRepository;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomManagementService {

    private static final String ROOM_NUMBER_UNIQUE_CONSTRAINT = "rooms_room_number_key";
    
    /** S1-10 AC3: bảo trì phải có lý do và khoảng ngày, chỉ nhập được ở biểu mẫu bảo trì (màn hình Phòng). */
    static final String USE_MAINTENANCE_FORM_MESSAGE =
            "Muốn chuyển phòng sang Bảo trì, hãy vào màn hình Phòng và chọn trạng thái Bảo trì để nhập lý do và khoảng ngày.";
    private final RoomManagementRepository roomRepository;
    private final RoomNoteRepository roomNoteRepository;
    private final FutureBookingImpactChecker bookingImpactChecker;

    public RoomManagementService(
            RoomManagementRepository roomRepository,
            RoomNoteRepository roomNoteRepository,
            FutureBookingImpactChecker bookingImpactChecker) {
        this.roomRepository = roomRepository;
        this.roomNoteRepository = roomNoteRepository;
        this.bookingImpactChecker = bookingImpactChecker;
    }

    @Transactional
    public RoomResponse create(CreatePhysicalRoomRequest request) {
        String roomNumber = normalize(request.roomNumber());
        String roomType = normalize(request.roomType());

        if (roomRepository.existsByRoomNumberIgnoreCase(roomNumber)) {
            throw new IllegalArgumentException("Số phòng " + roomNumber + " đã tồn tại trong hệ thống.");
        }
                if (request.status() == RoomStatus.BAO_TRI) {
            throw new IllegalArgumentException(USE_MAINTENANCE_FORM_MESSAGE);
        }

        Room room = new Room();
        room.setRoomNumber(roomNumber);
        room.setFloor(request.floor());
        room.setRoomType(roomType);
        room.setStatus(request.status() == null ? RoomStatus.TRONG_SACH : request.status());
        room.setActive(request.active() == null || request.active());

        Room saved;
        try {
            saved = roomRepository.saveAndFlush(room);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("Số phòng " + roomNumber + " đã tồn tại trong hệ thống.");
        }
        saveNote(saved, request.note() == null ? "" : request.note().trim());
        return RoomResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public RoomSearchResponse search(String roomType, Integer floor, RoomStatus status, Boolean active) {
        String normalizedRoomType = roomType == null || roomType.isBlank()
                ? null
                : roomType.trim();

        if (floor != null && floor < 0) {
            throw new IllegalArgumentException("Tầng phải lớn hơn hoặc bằng 0.");
        }

        List<RoomResponse> rooms = roomRepository
            .searchRooms(normalizedRoomType, floor, status, active)
                .stream()
                .map(RoomResponse::from)
                .toList();

        return new RoomSearchResponse(rooms, normalizedRoomType, floor, status, !rooms.isEmpty());
    }

    @Transactional
    public RoomUpdateResponse update(Long roomId, UpdatePhysicalRoomRequest request) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng với ID " + roomId + "."));

        String newRoomNumber = normalize(request.roomNumber());
        String newRoomType = normalize(request.roomType());
        String newNote = request.note() == null ? "" : request.note().trim();

        if (roomRepository.existsByRoomNumberIgnoreCaseAndIdNot(newRoomNumber, roomId)) {
            throw new IllegalArgumentException("Số phòng " + newRoomNumber + " đã tồn tại trong hệ thống.");
        }
                if (request.status() == RoomStatus.BAO_TRI && room.getStatus() != RoomStatus.BAO_TRI) {
            throw new IllegalArgumentException(USE_MAINTENANCE_FORM_MESSAGE);
        }

        FutureBookingImpactChecker.FutureBookingImpact impact = bookingImpactChecker.check(
                room.getId(),
                room.getRoomNumber(),
                newRoomNumber,
                room.getRoomType(),
                newRoomType,
                room.getFloor(),
                request.floor(),
                LocalDate.now());

        boolean changedSensitiveInformation = !room.getRoomNumber().equalsIgnoreCase(newRoomNumber)
                || !room.getRoomType().equalsIgnoreCase(newRoomType)
                || !room.getFloor().equals(request.floor());

        if (changedSensitiveInformation
                && !impact.checkAvailable()
                && !request.confirmWhenBookingCheckUnavailable()) {
            return new RoomUpdateResponse(
                    RoomResponse.from(room),
                    getNote(roomId),
                    false,
                    true,
                    0,
                    impact.message() + " Hãy xác nhận tiếp tục nếu bạn chắc chắn muốn lưu thay đổi.");
        }

        if (impact.warningRequired() && !request.confirmWhenBookingCheckUnavailable()) {
            return new RoomUpdateResponse(
                    RoomResponse.from(room),
                    getNote(roomId),
                    true,
                    true,
                    impact.affectedBookings(),
                    impact.message());
        }

        room.setRoomNumber(newRoomNumber);
        room.setFloor(request.floor());
        room.setRoomType(newRoomType);
        room.setActive(request.active());
        if (request.status() != null) {
            if (room.getStatus() == RoomStatus.BAO_TRI && request.status() != RoomStatus.BAO_TRI) {
                // Rời trạng thái bảo trì thì xoá lý do và khoảng ngày cũ (giống RoomService.updateStatus)
                room.setMaintenanceReason(null);
                room.setMaintenanceStartDate(null);
                room.setMaintenanceEndDate(null);
            }
            room.setStatus(request.status());
        }

        Room saved;
        try {
            saved = roomRepository.saveAndFlush(room);
        } catch (DataIntegrityViolationException exception) {
            if (!isRoomNumberUniqueConstraintViolation(exception)) {
                throw exception;
            }
            throw new IllegalArgumentException(
                    "Số phòng " + newRoomNumber + " đã tồn tại trong hệ thống.",
                    exception);
        }
        saveNote(saved, newNote);

        return new RoomUpdateResponse(
                RoomResponse.from(saved),
                newNote,
                impact.checkAvailable(),
                impact.warningRequired(),
                impact.affectedBookings(),
                impact.message());
    }

    @Transactional(readOnly = true)
    public String getNote(Long roomId) {
        return roomNoteRepository.findByRoomId(roomId)
                .map(note -> note.getNote())
                .orElse("");
    }

    private void saveNote(Room room, String note) {
        if (note == null || note.isBlank()) {
            roomNoteRepository.findByRoomId(room.getId()).ifPresent(roomNoteRepository::delete);
            return;
        }

        RoomNote roomNote = roomNoteRepository.findByRoomId(room.getId()).orElseGet(RoomNote::new);
        roomNote.setRoom(room);
        roomNote.setNote(note);
        roomNoteRepository.save(roomNote);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isRoomNumberUniqueConstraintViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return ROOM_NUMBER_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraintViolation.getConstraintName());
            }
        }
        return false;
    }
}
