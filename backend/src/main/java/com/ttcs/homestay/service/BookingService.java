package com.ttcs.homestay.service;

import java.util.Locale;
import java.util.UUID;

import com.ttcs.homestay.dto.booking.BookingCreateRequest;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.RoomTypeNotFoundException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ttcs.homestay.dto.booking.BookingListItemResponse;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final OperatingSettingsService operatingSettingsService;

    public BookingService(
            BookingRepository bookingRepository,
            RoomTypeRepository roomTypeRepository,
            OperatingSettingsService operatingSettingsService) {
        this.bookingRepository = bookingRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.operatingSettingsService = operatingSettingsService;
    }

    @Transactional
    public BookingResponse createBooking(BookingCreateRequest request) {
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày trả phòng phải sau ngày nhận phòng");
        }

        RoomType roomType = roomTypeRepository.findById(request.roomTypeId())
                .orElseThrow(RoomTypeNotFoundException::new);
        if (!Boolean.TRUE.equals(roomType.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Loại phòng đã ngừng bán");
        }
        Long weekdayPrice = roomType.getWeekdayPrice();
        Long weekendPrice = roomType.getWeekendPrice();
        if (weekdayPrice == null || weekdayPrice <= 0 || weekendPrice == null || weekendPrice <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Loại phòng cần có giá ngày thường và giá cuối tuần hợp lệ");
        }

        OffsetDateTime createdAt = OffsetDateTime.now();
        OperatingSettings settings = operatingSettingsService.findEffectiveAt(createdAt);
        Set<DayOfWeek> weekendDays = Arrays.stream(settings.getWeekendDays().split(","))
                .map(String::trim)
                .map(value -> DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toSet());

        long totalAmount = 0;
        for (LocalDate night = request.checkInDate(); night.isBefore(request.checkOutDate()); night = night.plusDays(1)) {
            long nightlyPrice = weekendDays.contains(night.getDayOfWeek()) ? weekendPrice : weekdayPrice;
            totalAmount = Math.addExact(totalAmount, nightlyPrice);
        }

        // Đã sửa: Xóa dòng khai báo trùng lặp "Booking booking = new Booking();"
        Booking booking = new Booking();

        booking.setBookingCode(
                "BK-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase(Locale.ROOT)
        );

        booking.setGuestName(request.guestName().trim());
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);

        booking.setRoomType(roomType);
        booking.setRoomTypeNameSnapshot(roomType.getName());
        booking.setCheckInDate(request.checkInDate());
        booking.setCheckOutDate(request.checkOutDate());
        booking.setWeekdayPriceSnapshot(weekdayPrice);
        booking.setWeekendPriceSnapshot(weekendPrice);
        booking.setWeekendDaysSnapshot(settings.getWeekendDays());
        booking.setTotalAmount(totalAmount);
        booking.setCreatedAt(createdAt);

        Booking savedBooking = bookingRepository.save(booking);

        // Đã sửa: Trả về BookingResponse sử dụng constructor của record thay vì gọi phương thức from() chưa tồn tại
        return new BookingResponse(
                savedBooking.getId(),
                savedBooking.getBookingCode(),
                savedBooking.getGuestName(),
                roomType.getId(),
                savedBooking.getRoomTypeNameSnapshot(),
                savedBooking.getCheckInDate(),
                savedBooking.getCheckOutDate(),
                savedBooking.getWeekdayPriceSnapshot(),
                savedBooking.getWeekendPriceSnapshot(),
                savedBooking.getWeekendDaysSnapshot(),
                savedBooking.getTotalAmount(),
                savedBooking.getStatus(),
                savedBooking.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<BookingListItemResponse> getLatestBookings() {
        return bookingRepository.findAllByOrderByCreatedAtDescIdDesc()
                .stream()
                .map(BookingListItemResponse::from)
                .toList();
    }
}