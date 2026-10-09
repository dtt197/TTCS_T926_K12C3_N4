package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcs.homestay.dto.booking.BookingCancelRequest;
import com.ttcs.homestay.dto.booking.GuestBookingRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** S3-02 Lát 3 (AC5): booking đã huỷ thôi chiếm chỗ, phòng trở lại kết quả tra phòng trống ngay. */
@SpringBootTest
@Transactional
class BookingCancelIntegrationTest {

    private static final LocalDate CHECK_IN = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(1);

    @Autowired
    private GuestBookingService guestBookingService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RoomAvailabilityService roomAvailabilityService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private OperatingSettingsRepository operatingSettingsRepository;

    private RoomType roomType;

    @BeforeEach
    void setUp() {
        operatingSettingsRepository.findAll().stream().findFirst().orElseGet(() -> {
            OperatingSettings settings = new OperatingSettings();
            settings.setHomestayName("HomeStay Test");
            settings.setCheckInTime(LocalTime.of(14, 0));
            settings.setCheckOutTime(LocalTime.of(12, 0));
            settings.setLateCheckoutFeePerHour(100_000L);
            settings.setExtraPersonFee(200_000L);
            settings.setCreatedByName("Hệ thống");
            settings.setCreatedAt(OffsetDateTime.now().minusDays(1));
            settings.setWeekendDays("FRIDAY,SATURDAY");
            return operatingSettingsRepository.save(settings);
        });

        roomType = new RoomType();
        roomType.setCode("CANCEL_TEST");
        roomType.setName("Phòng thử huỷ");
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(3);
        roomType.setNumberOfBeds(1);
        roomType.setStatus(true);
        roomType.setWeekdayPrice(500_000L);
        roomType.setWeekendPrice(700_000L);
        roomType = roomTypeRepository.save(roomType);

        Room room = new Room();
        room.setRoomNumber("CANCEL-1");
        room.setFloor(1);
        room.setRoomType(roomType.getName());
        room.setStatus(RoomStatus.TRONG_SACH);
        room.setActive(true);
        roomRepository.save(room);
    }

    private Booking book(String guest) {
        String code = guestBookingService.createGuestBooking(new GuestBookingRequest(
                roomType.getId(), CHECK_IN, CHECK_OUT, guest, "0912345678", "khach@example.test", 1, null, true))
                .bookingCode();
        return bookingRepository.findByBookingCode(code).orElseThrow();
    }

    @Test
    void huyBookingPhongCuoiCung_phongTroLaiTraPhongTrongNgayVaDatLaiDuocDungPhongDo() {
        Booking first = book("Khách đầu tiên");
        assertThat(roomAvailabilityService.availableRooms(roomType, CHECK_IN, CHECK_OUT)).isZero();

        bookingService.cancelBooking(first.getId(), new BookingCancelRequest("Khách báo huỷ chuyến"));

        assertThat(bookingRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.DA_HUY);
        assertThat(roomAvailabilityService.availableRooms(roomType, CHECK_IN, CHECK_OUT)).isEqualTo(1);
        Booking second = book("Khách đặt lại");
        assertThat(second.getRoom()).isNotNull();
        assertThat(second.getRoom().getId()).isEqualTo(first.getRoom().getId());
    }
}