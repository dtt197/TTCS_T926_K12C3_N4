package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.roomtype.RoomTypeImageResponse;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.RoomTypeImage;
import com.ttcs.homestay.exception.CannotDeleteLastImageException;
import com.ttcs.homestay.exception.InvalidImageException;
import com.ttcs.homestay.exception.MaxImageCountExceededException;
import com.ttcs.homestay.exception.RoomTypeImageNotFoundException;
import com.ttcs.homestay.exception.RoomTypeNotFoundException;
import com.ttcs.homestay.repository.RoomTypeImageRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    /**
     * S2-09 (Chức năng 2): Cập nhật thứ tự hiển thị của các ảnh sau khi kéo thả.
     * Quy tắc: ảnh ở vị trí đầu tiên sau khi sắp xếp tự động trở thành ảnh đại diện mới.
     */
    @Transactional
    public List<RoomTypeImageResponse> reorderImages(Long roomTypeId, List<Long> imageIds) {
        if (!roomTypeRepository.existsById(roomTypeId)) {
            throw new RoomTypeNotFoundException("Không tìm thấy loại phòng #" + roomTypeId);
        }

        if (imageIds == null || imageIds.isEmpty()) {
            throw new InvalidImageException("Danh sách thứ tự ảnh không được để trống.");
        }

        List<RoomTypeImage> existingImages = roomTypeImageRepository.findByRoomTypeIdOrderByDisplayOrderAsc(roomTypeId);
        if (existingImages.isEmpty()) {
            return List.of();
        }

        if (existingImages.size() != imageIds.size()) {
            throw new InvalidImageException("Số lượng ảnh cần sắp xếp không khớp với số ảnh hiện có.");
        }

        Map<Long, RoomTypeImage> imageMap = existingImages.stream()
                .collect(Collectors.toMap(RoomTypeImage::getId, img -> img));

        if (!imageMap.keySet().containsAll(imageIds)) {
            throw new InvalidImageException("Danh sách chứa ảnh không thuộc về loại phòng này.");
        }

        // Cập nhật displayOrder và isPrimary theo thứ tự mới:
        // Quy tắc hiển thị: ảnh nằm ở vị trí đầu tiên sau khi sắp xếp tự động trở thành ảnh đại diện mới
        for (int i = 0; i < imageIds.size(); i++) {
            Long imgId = imageIds.get(i);
            RoomTypeImage img = imageMap.get(imgId);
            img.setDisplayOrder(i);
            img.setIsPrimary(i == 0);
        }

        List<RoomTypeImage> updated = roomTypeImageRepository.saveAll(existingImages);
        return updated.stream()
                .sorted(Comparator.comparing(RoomTypeImage::getDisplayOrder))
                .map(RoomTypeImageResponse::from)
                .toList();
    }

    /**
     * S2-09: Xoá ảnh của loại phòng có bảo vệ.
     * Không cho phép xoá ảnh cuối cùng của loại phòng đang trong trạng thái mở bán.
     * Xoá tệp ảnh gốc & bản thu nhỏ, cập nhật lại thứ tự và ảnh đại diện mới.
     */
    @Transactional
    public List<RoomTypeImageResponse> deleteImage(Long roomTypeId, Long imageId) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new RoomTypeNotFoundException("Không tìm thấy loại phòng #" + roomTypeId));

        List<RoomTypeImage> existingImages = roomTypeImageRepository.findByRoomTypeIdOrderByDisplayOrderAsc(roomTypeId);

        RoomTypeImage targetImage = existingImages.stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new RoomTypeImageNotFoundException("Không tìm thấy ảnh #" + imageId + " của loại phòng này"));

        // Điều kiện bảo vệ: Chặn yêu cầu xoá nếu đó là ảnh cuối cùng duy nhất của loại phòng đang trong trạng thái mở bán
        boolean isActive = !Boolean.FALSE.equals(roomType.getStatus());
        if (isActive && existingImages.size() <= 1) {
            throw new CannotDeleteLastImageException(
                    "Không được phép xoá ảnh cuối cùng của loại phòng đang mở bán. Vui lòng ngừng bán loại phòng trước khi xoá ảnh này."
            );
        }

        // Xoá tệp ảnh gốc và bản thu nhỏ tương ứng khỏi hệ thống
        imageStorageService.deleteImageFile(targetImage.getImageUrl());
        imageStorageService.deleteImageFile(targetImage.getThumbnailUrl());

        // Xoá bản ghi khỏi cơ sở dữ liệu
        roomTypeImageRepository.delete(targetImage);

        // Cập nhật lại thứ tự hiển thị và ảnh đại diện mới cho các ảnh còn lại
        List<RoomTypeImage> remainingImages = existingImages.stream()
                .filter(img -> !img.getId().equals(imageId))
                .collect(Collectors.toList());

        for (int i = 0; i < remainingImages.size(); i++) {
            RoomTypeImage img = remainingImages.get(i);
            img.setDisplayOrder(i);
            img.setIsPrimary(i == 0); // Ảnh đầu tiên còn lại làm ảnh đại diện mới
        }

        List<RoomTypeImage> saved = roomTypeImageRepository.saveAll(remainingImages);
        return saved.stream()
                .sorted(Comparator.comparing(RoomTypeImage::getDisplayOrder))
                .map(RoomTypeImageResponse::from)
                .toList();
    }
}
