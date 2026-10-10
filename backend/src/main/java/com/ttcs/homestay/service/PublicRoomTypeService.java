package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.roomtype.PublicRoomTypeCard;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S2-03: danh sách loại phòng cho trang công khai. Thứ tự: giá từ tăng dần, chưa có giá xuống cuối, bằng giá thì theo tên. */
@Service
public class PublicRoomTypeService {

    private static final Comparator<PublicRoomTypeCard> DISPLAY_ORDER =
            Comparator.comparing((PublicRoomTypeCard card) -> card.fromPrice(),
                            Comparator.nullsLast(Comparator.<Long>naturalOrder()))
                    .thenComparing(card -> card.name(), String.CASE_INSENSITIVE_ORDER);

    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailabilityService roomAvailabilityService;

    public PublicRoomTypeService(
            RoomTypeRepository roomTypeRepository,
            RoomAvailabilityService roomAvailabilityService) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomAvailabilityService = roomAvailabilityService;
    }

    @Transactional(readOnly = true)
    public List<PublicRoomTypeCard> listRoomTypes() {
        return roomTypeRepository.findAllByStatusTrueOrderByCodeAsc().stream()
                .map(roomType -> PublicRoomTypeCard.from(
                        roomType, roomAvailabilityService.activeRoomCount(roomType)))
                .sorted(DISPLAY_ORDER)
                .toList();
    }
}