package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.RoomUnavailableException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    /** S3-02: tên ràng buộc loại trừ trong migration V36, cùng danh sách trạng thái với OCCUPYING_STATUSES. */
    static final String ROOM_OVERLAP_CONSTRAINT = "bookings_room_no_overlap";
    
    private static final Logger log = LoggerFactory.getLogger(RoomAvailabilityService.class);
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

    /**
     * S3-02 Lát 1: chọn phòng cụ thể cho booking trong khoảng ngày [checkIn, checkOut).
     * Phòng được chọn đang hoạt động, không bảo trì đêm nào và không bị booking đang chiếm phòng khác giữ.
     * Ưu tiên giữ phòng hiện tại của booking, sau đó chọn số phòng nhỏ nhất; không còn phòng thì báo hết phòng.
     */
    @Transactional
    public Room assignRoom(
            RoomType roomType, LocalDate checkIn, LocalDate checkOut, Long excludeBookingId, Room currentRoom) {
        List<Room> rooms = roomRepository.findByRoomTypeIgnoreCaseAndActiveTrue(roomType.getName());
        if (rooms.isEmpty()) {
            throw unavailable(roomType, checkIn, checkOut);
        }
        List<Booking> bookings =
                bookingRepository.findOverlappingOnRooms(rooms, checkIn, checkOut, OCCUPYING_STATUSES);
        OffsetDateTime now = OffsetDateTime.now();

        // Booking chờ xác nhận đã quá hạn giữ chỗ được chuyển sang hết hạn ngay để trả phòng (giống job S2-07 Lát 4).
        List<Booking> expired = bookings.stream().filter(booking -> isHoldExpired(booking, now)).toList();
        expired.forEach(booking -> booking.setStatus(BookingStatus.DA_HET_HAN));
        if (!expired.isEmpty()) {
            bookingRepository.saveAll(expired);
        }

        Set<Long> heldRoomIds = bookings.stream()
                .filter(booking -> booking.getStatus() != BookingStatus.DA_HET_HAN)
                .filter(booking -> excludeBookingId == null || !excludeBookingId.equals(booking.getId()))
                .filter(booking -> booking.getRoom() != null)
                .map(booking -> booking.getRoom().getId())
                .collect(Collectors.toSet());

        List<Room> freeRooms = rooms.stream()
                .filter(room -> !heldRoomIds.contains(room.getId()))
                .filter(room -> !isUnderMaintenanceAnyNight(room, checkIn, checkOut))
                .sorted(Comparator.comparing(Room::getRoomNumber))
                .toList();
        if (freeRooms.isEmpty()) {
            throw unavailable(roomType, checkIn, checkOut);
        }
        if (currentRoom != null) {
            for (Room room : freeRooms) {
                if (room.getId() != null && room.getId().equals(currentRoom.getId())) {
                    return room;
                }
            }
        }
        return freeRooms.get(0);
    }

    /** S3-02 Lát 1: lỗi do cơ sở dữ liệu từ chối vì hai booking cùng chiếm một phòng trong một đêm. */
    public static boolean isRoomOverlapViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(ROOM_OVERLAP_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    public static RoomUnavailableException unavailable(RoomType roomType) {
        return new RoomUnavailableException("Loại phòng " + roomType.getName()
                + " đã hết phòng trong khoảng ngày bạn chọn. Vui lòng chọn ngày hoặc loại phòng khác.");
    }
    
    /** S3-02 Lát 2: báo hết phòng và ghi log loại phòng, khoảng ngày của yêu cầu bị từ chối. */
    public static RoomUnavailableException unavailable(RoomType roomType, LocalDate checkIn, LocalDate checkOut) {
        log.info("Từ chối đặt phòng vì hết phòng: loại phòng {} (id {}), từ {} đến {}",
                roomType.getName(), roomType.getId(), checkIn, checkOut);
        return unavailable(roomType);
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

    private static boolean isUnderMaintenanceAnyNight(Room room, LocalDate checkIn, LocalDate checkOut) {
        for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
            if (isUnderMaintenance(room, night)) {
                return true;
            }
        }
        return false;
    }

    private static boolean occupies(Booking booking, LocalDate night, OffsetDateTime now) {
        boolean coversNight = !night.isBefore(booking.getCheckInDate()) && night.isBefore(booking.getCheckOutDate());
        return coversNight && !isHoldExpired(booking, now);
    }

    private static boolean isHoldExpired(Booking booking, OffsetDateTime now) {
        return booking.getStatus() == BookingStatus.CHO_XAC_NHAN
                && booking.getHoldExpiresAt() != null
                && !booking.getHoldExpiresAt().isAfter(now);
    }
}