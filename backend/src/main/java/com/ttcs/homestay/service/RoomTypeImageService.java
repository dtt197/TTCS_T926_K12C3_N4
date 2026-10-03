package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.roomtype.RoomTypeImageResponse;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.RoomTypeImage;
import com.ttcs.homestay.exception.MaxImageCountExceededException;
import com.ttcs.homestay.exception.RoomTypeNotFoundException;
import com.ttcs.homestay.repository.RoomTypeImageRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RoomTypeImageService {

    public static final int MAX_IMAGES_PER_ROOM_TYPE = 8;

    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final ImageStorageService imageStorageService;

    public RoomTypeImageService(
            RoomTypeRepository roomTypeRepository,
            RoomTypeImageRepository roomTypeImageRepository,
            ImageStorageService imageStorageService) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomTypeImageRepository = roomTypeImageRepository;
        this.imageStorageService = imageStorageService;
    }

    @Transactional
    public RoomTypeImageResponse uploadImage(Long roomTypeId, MultipartFile file) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new RoomTypeNotFoundException("Không tìm thấy loại phòng #" + roomTypeId));

        long currentCount = roomTypeImageRepository.countByRoomTypeId(roomTypeId);
        if (currentCount >= MAX_IMAGES_PER_ROOM_TYPE) {
            throw new MaxImageCountExceededException("Mỗi loại phòng chỉ được tối đa 8 ảnh.");
        }

        ImageStorageService.StoredImageResult stored =
                imageStorageService.storeRoomTypeImage(roomTypeId, file);

        // Đánh dấu ảnh tải lên đầu tiên làm ảnh đại diện
        boolean isFirst = (currentCount == 0);
        int displayOrder = (int) currentCount;
        boolean isPrimary = isFirst;

        RoomTypeImage roomTypeImage = new RoomTypeImage(
                roomType,
                stored.imageUrl(),
                stored.thumbnailUrl(),
                displayOrder,
                isPrimary
        );

        RoomTypeImage saved = roomTypeImageRepository.save(roomTypeImage);
        return RoomTypeImageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RoomTypeImageResponse> getImages(Long roomTypeId) {
        if (!roomTypeRepository.existsById(roomTypeId)) {
            throw new RoomTypeNotFoundException("Không tìm thấy loại phòng #" + roomTypeId);
        }

        return roomTypeImageRepository.findByRoomTypeIdOrderByDisplayOrderAsc(roomTypeId)
                .stream()
                .map(RoomTypeImageResponse::from)
                .toList();
    }
}
