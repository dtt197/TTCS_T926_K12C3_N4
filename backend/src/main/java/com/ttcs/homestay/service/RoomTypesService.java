package com.ttcs.homestay.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.RoomTypeRepository;

@Service
public class RoomTypesService {

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    public List<RoomType> getAllRoomTypes() {
        return roomTypeRepository.findAll();
    }

    public void createRoomType(RoomType roomType) {
        if (roomTypeRepository.existsByCode(roomType.getCode())) {
            throw new RuntimeException("Mã loại phòng đã tồn tại!");
        }
        roomTypeRepository.save(roomType);
    }

    public void saveRoomType(RoomType roomType) {
        roomTypeRepository.save(roomType);
    }

    public RoomType getRoomTypeById(Long id) {
        return roomTypeRepository.findById(id).orElse(null);
    }

    // 1. Chặn xoá nếu cần thiết
    public void deleteRoomType(Long id) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại phòng cần xóa!"));
        roomTypeRepository.deleteById(id);
    }

    // 2. Chức năng đánh dấu ngừng bán / mở bán lại
    public void toggleStatus(Long id) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại phòng!"));
        
        boolean currentStatus = roomType.getStatus() != null ? roomType.getStatus() : true;
        roomType.setStatus(!currentStatus);
        
        roomTypeRepository.save(roomType);
    }
}