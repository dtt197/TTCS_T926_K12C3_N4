package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S2-07 Lát 1: số phòng còn trống của một loại phòng trong khoảng ngày [checkIn, checkOut).
 * Quy tắc theo S2-05 để S2-05 dùng lại:
 * - Phòng tính là trống nếu đang hoạt động và không bảo trì trong đêm đó.
 * - Booking chờ xác nhận (còn hạn giữ chỗ), đã xác nhận hoặc đang ở thì chiếm một phòng mỗi đêm.
 * - Booking chờ xác nhận đã quá hạn giữ chỗ 24 giờ không còn chiếm phòng.
 */
@Service
public class RoomAvailabilityService {

    static final Set<BookingStatus> OCCUPYING_STATUSES =
            Set.of(BookingStatus.CHO_XAC_NHAN, BookingStatus.DA_XAC_NHAN, BookingStatus.DA_NHAN_PHONG);

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public RoomAvailabilityService(RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    /** Số phòng trống suốt cả khoảng ngày = đêm ít phòng trống nhất. */
    @Transactional(readOnly = true)
    public int availableRooms(RoomType roomType, LocalDate checkIn, LocalDate checkOut) {
        return availableRooms(roomType, checkIn, checkOut, null);
    }

    /**
     * S3-04: kiểm tra số phòng trống khi thay đổi booking, loại trừ booking đang xét để không tự chặn chính nó.
     */
    @Transactional(readOnly = true)
    public int availableRooms(RoomType roomType, LocalDate checkIn, LocalDate checkOut, Long excludeBookingId) {
        List<Room> rooms = roomRepository.findByRoomTypeIgnoreCaseAndActiveTrue(roomType.getName());
        List<Booking> bookings =
                bookingRepository.findOverlapping(roomType.getId(), checkIn, checkOut, OCCUPYING_STATUSES);
        OffsetDateTime now = OffsetDateTime.now();

        long minAvailable = Long.MAX_VALUE;
        for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
            LocalDate currentNight = night;
            long freeRooms = rooms.stream().filter(room -> !isUnderMaintenance(room, currentNight)).count();
            long occupied = bookings.stream()
                    .filter(booking -> excludeBookingId == null || !excludeBookingId.equals(booking.getId()))
                    .filter(booking -> occupies(booking, currentNight, now))
                    .count();
            minAvailable = Math.min(minAvailable, freeRooms - occupied);
        }
        return (int) Math.max(minAvailable, 0);
    }

    /** Số phòng vật lý đang hoạt động, không phụ thuộc khoảng ngày được chọn. */
    @Transactional(readOnly = true)
    public long activeRoomCount(RoomType roomType) {
        return roomRepository.countByRoomTypeIgnoreCaseAndActiveTrue(roomType.getName());
    }

    /** Bảo trì từ ngày bắt đầu đến hết ngày kết thúc; trạng thái bảo trì mà không có ngày thì coi như bảo trì mọi đêm. */
    private static boolean isUnderMaintenance(Room room, LocalDate night) {
        if (room.getMaintenanceStartDate() == null) {
            return room.getStatus() == RoomStatus.BAO_TRI;
        }
        return !night.isBefore(room.getMaintenanceStartDate())
                && (room.getMaintenanceEndDate() == null || !night.isAfter(room.getMaintenanceEndDate()));
    }

    private static boolean occupies(Booking booking, LocalDate night, OffsetDateTime now) {
        boolean coversNight = !night.isBefore(booking.getCheckInDate()) && night.isBefore(booking.getCheckOutDate());
        boolean holdExpired = booking.getStatus() == BookingStatus.CHO_XAC_NHAN
                && booking.getHoldExpiresAt() != null
                && !booking.getHoldExpiresAt().isAfter(now);
        return coversNight && !holdExpired;
    }
}