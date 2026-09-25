package com.ttcs.homestay.dto;

import com.ttcs.homestay.entity.RoomStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateRoomStatusRequest(@NotNull RoomStatus status) {
}