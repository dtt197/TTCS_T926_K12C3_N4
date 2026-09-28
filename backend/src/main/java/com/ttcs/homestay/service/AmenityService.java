package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.amenity.AmenityRequest;
import com.ttcs.homestay.dto.amenity.AmenityResponse;
import com.ttcs.homestay.entity.Amenity;
import com.ttcs.homestay.exception.AmenityConflictException;
import com.ttcs.homestay.exception.AmenityNotFoundException;
import com.ttcs.homestay.repository.AmenityRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S1-08: quản lý danh mục tiện nghi (thêm, sửa, ngừng dùng, xoá khi chưa gắn cho loại phòng nào). */
@Service
public class AmenityService {

    private final AmenityRepository amenityRepository;
    private final RoomTypeRepository roomTypeRepository;

    public AmenityService(AmenityRepository amenityRepository, RoomTypeRepository roomTypeRepository) {
        this.amenityRepository = amenityRepository;
        this.roomTypeRepository = roomTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<AmenityResponse> listAmenities() {
        return amenityRepository.findAllByOrderByCodeAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AmenityResponse createAmenity(AmenityRequest request) {
        String code = normalizeCode(request.code());
        if (amenityRepository.existsByCodeIgnoreCase(code)) {
            throw new AmenityConflictException("Mã tiện nghi " + code + " đã tồn tại, vui lòng chọn mã khác");
        }
        Amenity amenity = new Amenity();
        apply(amenity, request, code);
        amenity.setActive(true);
        return toResponse(amenityRepository.save(amenity));
    }

    @Transactional
    public AmenityResponse updateAmenity(Long id, AmenityRequest request) {
        Amenity amenity = findOrThrow(id);
        String code = normalizeCode(request.code());
        if (amenityRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new AmenityConflictException("Mã tiện nghi " + code + " đã tồn tại, vui lòng chọn mã khác");
        }
        apply(amenity, request, code);
        return toResponse(amenity);
    }

    /** AC1, AC3: ngừng dùng thay cho xoá; tiện nghi ngừng dùng không hiện ở loại phòng (AC4). */
    @Transactional
    public AmenityResponse updateStatus(Long id, boolean active) {
        Amenity amenity = findOrThrow(id);
        amenity.setActive(active);
        return toResponse(amenity);
    }

    /** AC3: tiện nghi đang được gắn cho loại phòng thì không xoá được, chỉ ngừng dùng. */
    @Transactional
    public void deleteAmenity(Long id) {
        Amenity amenity = findOrThrow(id);
        long roomTypeCount = roomTypeRepository.countByAmenitiesId(id);
        if (roomTypeCount > 0) {
            throw new AmenityConflictException("Tiện nghi \"" + amenity.getName() + "\" đang được gắn cho "
                    + roomTypeCount + " loại phòng nên không xoá được. Bạn có thể ngừng dùng.");
        }
        amenityRepository.delete(amenity);
    }

    private Amenity findOrThrow(Long id) {
        return amenityRepository.findById(id).orElseThrow(AmenityNotFoundException::new);
    }

    private void apply(Amenity amenity, AmenityRequest request, String code) {
        amenity.setCode(code);
        amenity.setName(request.name().trim());
        amenity.setIcon(request.icon().trim());
    }

    /** Mã lưu thống nhất bằng chữ in hoa, ví dụ "wifi" thành "WIFI". */
    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private AmenityResponse toResponse(Amenity amenity) {
        return AmenityResponse.from(amenity, roomTypeRepository.countByAmenitiesId(amenity.getId()));
    }
}