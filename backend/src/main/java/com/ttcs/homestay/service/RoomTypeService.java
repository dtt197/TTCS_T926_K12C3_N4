package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.roomtype.RoomTypeRequest;
import com.ttcs.homestay.dto.roomtype.RoomTypeResponse;
import com.ttcs.homestay.entity.Amenity;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.AmenityNotFoundException;
import com.ttcs.homestay.exception.InvalidRoomTypeCapacityException;
import com.ttcs.homestay.exception.RoomTypeConflictException;
import com.ttcs.homestay.exception.RoomTypeNotFoundException;
import com.ttcs.homestay.repository.AmenityRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S1-06: quản lý loại phòng. S1-08: gắn / bỏ tiện nghi cho loại phòng.
 * Phòng gắn với loại phòng qua tên (rooms.room_type = room_types.name, không phân biệt hoa thường).
 */
@Service
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final AmenityRepository amenityRepository;

    public RoomTypeService(RoomTypeRepository roomTypeRepository, RoomRepository roomRepository,
            AmenityRepository amenityRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.amenityRepository = amenityRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomTypeResponse> listRoomTypes() {
        return roomTypeRepository.findAllByOrderByCodeAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RoomTypeResponse createRoomType(RoomTypeRequest request) {
        String code = normalizeCode(request.code());
        String name = request.name().trim();
        validateCapacity(request);
        if (roomTypeRepository.existsByCodeIgnoreCase(code)) {
            throw new RoomTypeConflictException("Mã loại phòng " + code + " đã tồn tại, vui lòng chọn mã khác");
        }
        if (roomTypeRepository.existsByNameIgnoreCase(name)) {
            throw new RoomTypeConflictException("Tên loại phòng \"" + name + "\" đã có, vui lòng chọn tên khác");
        }

        RoomType roomType = new RoomType();
        apply(roomType, request, code, name);
        applyAmenities(roomType, request.amenityIds());
        roomType.setStatus(request.active() == null || request.active());
        return toResponse(roomTypeRepository.save(roomType));
    }

    @Transactional
    public RoomTypeResponse updateRoomType(Long id, RoomTypeRequest request) {
        RoomType roomType = findOrThrow(id);
        String code = normalizeCode(request.code());
        String name = request.name().trim();
        validateCapacity(request);
        if (roomTypeRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new RoomTypeConflictException("Mã loại phòng " + code + " đã tồn tại, vui lòng chọn mã khác");
        }
        if (roomTypeRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new RoomTypeConflictException("Tên loại phòng \"" + name + "\" đã có, vui lòng chọn tên khác");
        }

        String oldName = roomType.getName();
        apply(roomType, request, code, name);
        applyAmenities(roomType, request.amenityIds());
        if (!oldName.equals(name)) {
            // Đổi tên thì đổi luôn tên loại phòng lưu trong các phòng để không mất liên kết.
            roomRepository.renameRoomType(oldName, name);
        }
        return toResponse(roomType);
    }

    /** AC4: loại phòng không xoá được thì đánh dấu ngừng bán (active = false) hoặc bán lại. */
    @Transactional
    public RoomTypeResponse updateStatus(Long id, boolean active) {
        RoomType roomType = findOrThrow(id);
        roomType.setStatus(active);
        return toResponse(roomType);
    }

    /** AC4: loại phòng đang có phòng gắn vào thì không xoá được. */
    @Transactional
    public void deleteRoomType(Long id) {
        RoomType roomType = findOrThrow(id);
        long roomCount = roomRepository.countByRoomTypeIgnoreCase(roomType.getName());
        if (roomCount > 0) {
            throw new RoomTypeConflictException("Loại phòng \"" + roomType.getName() + "\" đang có " + roomCount
                    + " phòng gắn vào nên không xoá được. Bạn có thể đánh dấu ngừng bán.");
        }
        roomTypeRepository.delete(roomType);
    }

    /** S1-08 AC2: gắn tiện nghi cho loại phòng; gắn trùng hoặc gắn tiện nghi đã ngừng dùng bị chặn. */
    @Transactional
    public RoomTypeResponse addAmenity(Long roomTypeId, Long amenityId) {
        RoomType roomType = findOrThrow(roomTypeId);
        Amenity amenity = amenityRepository.findById(amenityId).orElseThrow(AmenityNotFoundException::new);
        if (!amenity.isActive()) {
            throw new RoomTypeConflictException(
                    "Tiện nghi \"" + amenity.getName() + "\" đã ngừng dùng nên không gắn được");
        }
        boolean alreadyAttached = roomType.getAmenities().stream()
                .anyMatch(existing -> existing.getId().equals(amenityId));
        if (alreadyAttached) {
            throw new RoomTypeConflictException("Loại phòng \"" + roomType.getName()
                    + "\" đã có tiện nghi \"" + amenity.getName() + "\"");
        }
        roomType.getAmenities().add(amenity);
        return toResponse(roomType);
    }

    /** S1-08: bỏ một tiện nghi khỏi loại phòng. */
    @Transactional
    public RoomTypeResponse removeAmenity(Long roomTypeId, Long amenityId) {
        RoomType roomType = findOrThrow(roomTypeId);
        roomType.getAmenities().removeIf(existing -> existing.getId().equals(amenityId));
        return toResponse(roomType);
    }
        /**
     * S1-08: tick chọn tiện nghi ngay trong biểu mẫu thêm / sửa loại phòng.
     * amenityIds = null: giữ nguyên. Tiện nghi đã ngừng dùng đang gắn thì giữ lại
     * (giao diện không hiện chúng nên không bỏ nhầm). Tick tiện nghi đã ngừng dùng thì bị chặn.
     */
    private void applyAmenities(RoomType roomType, List<Long> amenityIds) {
        if (amenityIds == null) {
            return;
        }
        Set<Long> wanted = new LinkedHashSet<>(amenityIds);
        roomType.getAmenities().removeIf(existing -> existing.isActive() && !wanted.contains(existing.getId()));
        for (Long amenityId : wanted) {
            boolean attached = roomType.getAmenities().stream()
                    .anyMatch(existing -> existing.getId().equals(amenityId));
            if (attached) {
                continue;
            }
            Amenity amenity = amenityRepository.findById(amenityId).orElseThrow(AmenityNotFoundException::new);
            if (!amenity.isActive()) {
                throw new RoomTypeConflictException(
                        "Tiện nghi \"" + amenity.getName() + "\" đã ngừng dùng nên không gắn được");
            }
            roomType.getAmenities().add(amenity);
        }
    }

    private RoomType findOrThrow(Long id) {
        return roomTypeRepository.findById(id).orElseThrow(RoomTypeNotFoundException::new);
    }

    /** AC2: sức chứa tối đa không được nhỏ hơn sức chứa tiêu chuẩn. */
    private void validateCapacity(RoomTypeRequest request) {
        if (request.maxCapacity() < request.standardCapacity()) {
            throw new InvalidRoomTypeCapacityException(request.standardCapacity(), request.maxCapacity());
        }
    }

    private void apply(RoomType roomType, RoomTypeRequest request, String code, String name) {
        roomType.setCode(code);
roomType.setName(name);
roomType.setStandardCapacity(request.standardCapacity());
roomType.setMaxCapacity(request.maxCapacity());
roomType.setNumberOfBeds(request.numberOfBeds());
roomType.setWeekdayPrice(request.weekdayPrice());
roomType.setWeekendPrice(request.weekendPrice());

String description =
        request.description() == null
                ? null
                : request.description().trim();

roomType.setDescription(
        description == null || description.isEmpty()
                ? null
                : description
);
    }

    /** Mã lưu thống nhất bằng chữ in hoa, ví dụ "doi" thành "DOI". */
    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private RoomTypeResponse toResponse(RoomType roomType) {
        return RoomTypeResponse.from(roomType, roomRepository.countByRoomTypeIgnoreCase(roomType.getName()));
    }
}