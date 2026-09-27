package com.ttcs.homestay.dto;

import com.ttcs.homestay.entity.RoomStatus;
import java.util.List;

public record RoomSearchResponse(
        List<RoomResponse> rooms,
        String appliedRoomType,
        Integer appliedFloor,
        RoomStatus appliedStatus,
        boolean hasResult
) {
}
