package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.RoomStatus;

public final class RoomStatusPolicy {

    private RoomStatusPolicy() {
    }

    public static boolean canCheckIn(RoomStatus status) {
        return status == RoomStatus.TRONG_SACH;
    }

    public static boolean canChangeTo(RoomStatus currentStatus, RoomStatus targetStatus) {
        return !(currentStatus == RoomStatus.DANG_O && targetStatus == RoomStatus.BAO_TRI);
    }

    public static String displayName(RoomStatus status) {
        return switch (status) {
            case TRONG_SACH -> "Trống sạch";
            case TRONG_BAN -> "Trống bẩn";
            case DANG_O -> "Đang ở";
            case BAO_TRI -> "Bảo trì";
        };
    }
}