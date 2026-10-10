package com.ttcs.homestay.service;

import com.ttcs.homestay.config.RoomShortageRuleConfig;
import com.ttcs.homestay.dto.booking.RoomShortageAlertResponse;
import com.ttcs.homestay.dto.booking.ShortageBookingResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task #s3-08: Service phát hiện các ngày và loại phòng có số booking còn hiệu lực vượt số phòng khả dụng.
 */
@Service
public class RoomShortageAlertService {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public RoomShortageAlertService(
            RoomTypeRepository roomTypeRepository,
            RoomRepository roomRepository,
            BookingRepository bookingRepository
    ) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Lấy danh sách cảnh báo thiếu phòng tính từ hôm nay đến hôm nay + SCAN_DAYS_AHEAD.
     */
    @Transactional(readOnly = true)
    public List<RoomShortageAlertResponse> getShortageAlerts() {
        return getShortageAlerts(LocalDate.now(), OffsetDateTime.now());
    }

    /**
     * Phương thức có tham số ngày mốc để phục vụ kiểm thử tự động chính xác.
     */
    @Transactional(readOnly = true)
    public List<RoomShortageAlertResponse> getShortageAlerts(LocalDate today, OffsetDateTime now) {
        LocalDate startDate = today;
        LocalDate endDate = today.plusDays(RoomShortageRuleConfig.SCAN_DAYS_AHEAD);

        List<RoomType> roomTypes = roomTypeRepository.findAll();
        if (roomTypes.isEmpty()) {
            return Collections.emptyList();
        }

        List<Room> allRooms = roomRepository.findAll();

        // 1. Tính số phòng khả dụng theo từng loại phòng tại thời điểm tải màn hình:
        // Số phòng khả dụng = tổng số phòng thực có - (bảo trì + đang khoá + ngừng bán).
        Map<Long, Long> availableRoomsByRoomTypeId = new HashMap<>();
        Map<String, RoomType> roomTypeByNameLower = new HashMap<>();

        for (RoomType rt : roomTypes) {
            roomTypeByNameLower.put(rt.getName().trim().toLowerCase(), rt);

            if (Boolean.FALSE.equals(rt.getStatus())) {
                // Nếu loại phòng ngừng bán hoàn toàn
                availableRoomsByRoomTypeId.put(rt.getId(), 0L);
                continue;
            }

            long availableCount = allRooms.stream()
                    .filter(r -> r.getRoomType() != null && r.getRoomType().equalsIgnoreCase(rt.getName()))
                    .filter(room -> room.isActive()) // Loại trừ phòng đang khoá / ngừng bán
                    .filter(r -> !RoomShortageRuleConfig.UNAVAILABLE_ROOM_STATUSES.contains(r.getStatus())) // Loại trừ phòng bảo trì
                    .count();

            availableRoomsByRoomTypeId.put(rt.getId(), availableCount);
        }

        // 2. Truy vấn danh sách booking còn hiệu lực trong khoảng quét
        List<Booking> bookings = bookingRepository.findActiveBookingsInDateRange(
                startDate,
                endDate,
                RoomShortageRuleConfig.VALID_BOOKING_STATUSES
        );

        // 3. Đếm số booking còn hiệu lực theo từng (ngày, loại phòng)
        // Key: date_roomTypeId -> count
        Map<String, Long> bookingCounts = new HashMap<>();

        for (Booking booking : bookings) {
            // Kiểm tra loại trừ nếu booking chờ xác nhận đã quá hạn giữ chỗ 24h
            if (RoomShortageRuleConfig.EXCLUDE_EXPIRED_HOLD
                    && booking.getStatus() == BookingStatus.CHO_XAC_NHAN
                    && booking.getHoldExpiresAt() != null
                    && !booking.getHoldExpiresAt().isAfter(now)) {
                continue;
            }

            Long targetRoomTypeId = null;
            if (booking.getRoomType() != null) {
                targetRoomTypeId = booking.getRoomType().getId();
            } else if (booking.getRoomTypeNameSnapshot() != null) {
                RoomType matched = roomTypeByNameLower.get(booking.getRoomTypeNameSnapshot().trim().toLowerCase());
                if (matched != null) {
                    targetRoomTypeId = matched.getId();
                }
            }

            if (targetRoomTypeId == null) {
                continue;
            }

            LocalDate checkIn = booking.getCheckInDate();
            LocalDate checkOut = booking.getCheckOutDate();

            if (checkIn == null || checkOut == null || !checkIn.isBefore(checkOut)) {
                continue;
            }

            // Booking nhiều đêm tính vào TỪNG đêm lưu trú: từ checkIn đến checkOut - 1. Ngày trả phòng KHÔNG tính.
            for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
                // Chỉ đếm nếu đêm này nằm trong khoảng quét [startDate, endDate]
                if (!night.isBefore(startDate) && !night.isAfter(endDate)) {
                    String key = makeKey(night, targetRoomTypeId);
                    bookingCounts.put(key, bookingCounts.getOrDefault(key, 0L) + 1);
                }
            }
        }

        // 4. So sánh số booking và số phòng khả dụng, chỉ giữ lại các cặp có booking > khả dụng
        List<RoomShortageAlertResponse> alerts = new ArrayList<>();

        for (LocalDate night = startDate; !night.isAfter(endDate); night = night.plusDays(1)) {
            for (RoomType rt : roomTypes) {
                long available = availableRoomsByRoomTypeId.getOrDefault(rt.getId(), 0L);
                long bookingCount = bookingCounts.getOrDefault(makeKey(night, rt.getId()), 0L);

                if (bookingCount > available) {
                    alerts.add(new RoomShortageAlertResponse(
                            night,
                            rt.getCode(),
                            rt.getName(),
                            bookingCount,
                            available
                    ));
                }
            }
        }

        // 5. Sắp xếp: ngày gần nhất trước (date ASC), cùng ngày sắp xếp theo tên loại phòng
        alerts.sort(Comparator.comparing((RoomShortageAlertResponse alert) -> alert.date())
                .thenComparing(alert -> alert.roomTypeName()));

        return alerts;
    }

    /**
     * S3-08: Lấy danh sách booking còn hiệu lực chiếm loại phòng đó trong ngày được chọn.
     * Dùng cùng quy tắc "còn hiệu lực" và quy tắc tính đêm của Lát 1.
     * Booking tạo sau cùng lên đầu để ưu tiên xử lý.
     */
    @Transactional(readOnly = true)
    public List<ShortageBookingResponse> getShortageBookings(LocalDate date, String roomTypeCode) {
        return getShortageBookings(date, roomTypeCode, OffsetDateTime.now());
    }

    /**
     * Phương thức có tham số thời gian mốc để phục vụ kiểm thử tự động.
     */
    @Transactional(readOnly = true)
    public List<ShortageBookingResponse> getShortageBookings(LocalDate date, String roomTypeCode, OffsetDateTime now) {
        // Tìm loại phòng theo mã
        RoomType roomType = roomTypeRepository.findByCodeIgnoreCase(roomTypeCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy loại phòng: " + roomTypeCode));

        // Truy vấn booking còn hiệu lực có đêm trùng ngày được chọn.
        // Một booking chiếm đêm date khi checkInDate <= date < checkOutDate.
        // Truy vấn: checkInDate <= date AND checkOutDate > date AND status IN (valid statuses)
        // findActiveBookingsInDateRange dùng: checkIn < endDate AND checkOut > startDate
        // Để lấy đúng các booking chiếm đêm "date" (checkIn <= date < checkOut), cần:
        //   checkOut > date  => startDate = date
        //   checkIn <= date  => checkIn < date+1  => endDate = date.plusDays(1)
        List<Booking> bookings = bookingRepository.findActiveBookingsInDateRange(
                date,                   // startDate: checkOut > date
                date.plusDays(1),       // endDate: checkIn < date+1 (tức checkIn <= date)
                RoomShortageRuleConfig.VALID_BOOKING_STATUSES
        );

        // Lọc theo loại phòng và áp dụng quy tắc loại trừ expired hold
        List<ShortageBookingResponse> result = bookings.stream()
                .filter(booking -> {
                    // Loại trừ booking chờ xác nhận đã quá hạn giữ chỗ 24h
                    if (RoomShortageRuleConfig.EXCLUDE_EXPIRED_HOLD
                            && booking.getStatus() == BookingStatus.CHO_XAC_NHAN
                            && booking.getHoldExpiresAt() != null
                            && !booking.getHoldExpiresAt().isAfter(now)) {
                        return false;
                    }
                    return true;
                })
                .filter(booking -> {
                    // Lọc theo loại phòng: so khớp bằng roomType entity hoặc roomTypeNameSnapshot
                    if (booking.getRoomType() != null) {
                        return booking.getRoomType().getId().equals(roomType.getId());
                    }
                    if (booking.getRoomTypeNameSnapshot() != null) {
                        return booking.getRoomTypeNameSnapshot().trim().equalsIgnoreCase(roomType.getName().trim());
                    }
                    return false;
                })
                .filter(booking -> {
                    // Kiểm tra booking thực sự chiếm đêm ngày được chọn: checkIn <= date < checkOut
                    LocalDate checkIn = booking.getCheckInDate();
                    LocalDate checkOut = booking.getCheckOutDate();
                    if (checkIn == null || checkOut == null || !checkIn.isBefore(checkOut)) {
                        return false;
                    }
                    return !date.isBefore(checkIn) && date.isBefore(checkOut);
                })
                .map(ShortageBookingResponse::from)
                .collect(Collectors.toList());

        // Sắp xếp: booking tạo sau cùng lên đầu (createdAt DESC) để ưu tiên xử lý
        result.sort(Comparator.comparing(ShortageBookingResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));

        return result;
    }

    private static String makeKey(LocalDate date, Long roomTypeId) {
        return date + "_" + roomTypeId;
    }
}

