package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.RoomType;

/** S2-07 Lát 1: loại phòng khách chọn được trên trang đặt phòng (đang bán và đã có giá). */
public record PublicRoomTypeOption(Long id, String code, String name, Integer maxCapacity) {

    public static PublicRoomTypeOption from(RoomType roomType) {
        return new PublicRoomTypeOption(roomType.getId(), roomType.getCode(), roomType.getName(),
                roomType.getMaxCapacity());
    }
}