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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomManagementService {

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

        Room room = new Room();
        room.setRoomNumber(roomNumber);
        room.setFloor(request.floor());
        room.setRoomType(roomType);
        room.setStatus(request.status() == null ? RoomStatus.TRONG_SACH : request.status());
        room.setActive(true);

        try {
            return RoomResponse.from(roomRepository.saveAndFlush(room));
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("Số phòng " + roomNumber + " đã tồn tại trong hệ thống.");
        }
    }

    @Transactional(readOnly = true)
    public RoomSearchResponse search(String roomType, Integer floor, RoomStatus status) {
        String normalizedRoomType = roomType == null || roomType.isBlank()
                ? null
                : roomType.trim();

        if (floor != null && floor < 0) {
            throw new IllegalArgumentException("Tầng phải lớn hơn hoặc bằng 0.");
        }

        List<RoomResponse> rooms = roomRepository
                .searchActiveRooms(normalizedRoomType, floor, status)
                .stream()
                .map(RoomResponse::from)
                .toList();

        return new RoomSearchResponse(rooms, normalizedRoomType, floor, status, !rooms.isEmpty());
    }

    @Transactional
    public RoomUpdateResponse update(Long roomId, UpdatePhysicalRoomRequest request) {
        Room room = roomRepository.findByIdAndActiveTrue(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng đang hoạt động với ID " + roomId + "."));

        String newRoomNumber = normalize(request.roomNumber());
        String newRoomType = normalize(request.roomType());
        String newNote = request.note() == null ? "" : request.note().trim();

        if (roomRepository.existsByRoomNumberIgnoreCaseAndIdNot(newRoomNumber, roomId)) {
            throw new IllegalArgumentException("Số phòng " + newRoomNumber + " đã được sử dụng bởi phòng khác.");
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
            room.setStatus(request.status());
        }

        Room saved = roomRepository.saveAndFlush(room);
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
                .map(RoomNote::getNote)
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
}
