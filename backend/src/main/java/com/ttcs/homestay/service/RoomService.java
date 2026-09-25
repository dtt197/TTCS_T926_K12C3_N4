package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.CheckInRequest;
import com.ttcs.homestay.dto.CheckInResponse;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.entity.CheckIn;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.exception.RoomNotFoundException;
import com.ttcs.homestay.exception.RoomStatusConflictException;
import com.ttcs.homestay.repository.CheckInRepository;
import com.ttcs.homestay.repository.RoomRepository;
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

    public RoomService(RoomRepository roomRepository, CheckInRepository checkInRepository) {
        this.roomRepository = roomRepository;
        this.checkInRepository = checkInRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRooms() {
        return roomRepository.findAllByActiveTrueOrderByRoomNumberAsc()
                .stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Transactional
    public RoomResponse updateStatus(Long roomId, RoomStatus targetStatus) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (!RoomStatusPolicy.canChangeTo(room.getStatus(), targetStatus)) {
            throw new RoomStatusConflictException(
                    "Không thể chuyển phòng " + room.getRoomNumber()
                            + " từ Đang ở sang Bảo trì. Hãy trả phòng trước.");
        }

        room.setStatus(targetStatus);
        return RoomResponse.from(roomRepository.save(room));
    }

    @Transactional
    public CheckInResponse checkIn(Long roomId, CheckInRequest request) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (!RoomStatusPolicy.canCheckIn(room.getStatus())) {
            throw new RoomStatusConflictException(
                    "Không thể nhận phòng " + room.getRoomNumber() + " vì phòng đang ở trạng thái "
                            + RoomStatusPolicy.displayName(room.getStatus())
                            + ". Chỉ phòng Trống sạch mới được gán khi nhận phòng.");
        }

        room.setStatus(RoomStatus.DANG_O);
        roomRepository.save(room);

        CheckIn checkIn = new CheckIn();
        checkIn.setRoom(room);
        checkIn.setGuestName(request.guestName().trim());
        checkIn.setCheckedInAt(OffsetDateTime.now(BUSINESS_ZONE));
        return CheckInResponse.from(checkInRepository.save(checkIn));
    }
}