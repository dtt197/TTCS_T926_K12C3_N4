package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.BookingUpdateRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BookingUpdateIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    private Booking existingBooking;
    private RoomType originalRoomType;
    private RoomType newRoomType;

    @BeforeEach
    void setUp() {
        originalRoomType = saveRoomType("UPDATE_ORIGINAL", "Phòng ban đầu");
        newRoomType = saveRoomType("UPDATE_NEW", "Phòng mới");

        existingBooking = new Booking();
        existingBooking.setBookingCode("UPDATEBK01");
        existingBooking.setGuestName("Khách cập nhật");
        existingBooking.setStatus(BookingStatus.DA_XAC_NHAN);
        existingBooking.setRoomType(originalRoomType);
        existingBooking.setRoomTypeNameSnapshot(originalRoomType.getName());
        existingBooking.setCheckInDate(LocalDate.of(2027, 6, 10));
        existingBooking.setCheckOutDate(LocalDate.of(2027, 6, 13));
        existingBooking.setWeekdayPriceSnapshot(500_000L);
        existingBooking.setWeekendPriceSnapshot(700_000L);
        existingBooking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        existingBooking.setTotalAmount(1_500_000L);
        existingBooking.setCreatedAt(OffsetDateTime.now());
        existingBooking = bookingRepository.saveAndFlush(existingBooking);
    }

    private RoomType saveRoomType(String code, String name) {
        RoomType roomType = new RoomType();
        roomType.setCode(code);
        roomType.setName(name);
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(3);
        roomType.setNumberOfBeds(1);
        return roomTypeRepository.save(roomType);
    }

    @Test
    void updatesStayDatesAndPersistsOnTheExistingBooking() {
        Long bookingId = existingBooking.getId();

        BookingResponse response = bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        originalRoomType.getId(),
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 5)));

        Booking saved = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(response.id()).isEqualTo(bookingId);
        assertThat(saved.getCheckInDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(saved.getCheckOutDate()).isEqualTo(LocalDate.of(2027, 7, 5));
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(saved.getTotalAmount()).isEqualTo(1_500_000L);
    }

    @Test
    void updatesRoomTypeAndPersistsItsNameOnTheExistingBooking() {
        Long bookingId = existingBooking.getId();

        BookingResponse response = bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        newRoomType.getId(),
                        existingBooking.getCheckInDate(),
                        existingBooking.getCheckOutDate()));

        Booking saved = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(response.roomTypeId()).isEqualTo(newRoomType.getId());
        assertThat(saved.getRoomType().getId()).isEqualTo(newRoomType.getId());
        assertThat(saved.getRoomTypeNameSnapshot()).isEqualTo(newRoomType.getName());
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void updatesDatesAndRoomTypeTogether() {
        Long bookingId = existingBooking.getId();

        BookingResponse response = bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        newRoomType.getId(),
                        LocalDate.of(2027, 8, 2),
                        LocalDate.of(2027, 8, 6)));

        Booking saved = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(response.id()).isEqualTo(bookingId);
        assertThat(saved.getCheckInDate()).isEqualTo(LocalDate.of(2027, 8, 2));
        assertThat(saved.getCheckOutDate()).isEqualTo(LocalDate.of(2027, 8, 6));
        assertThat(saved.getRoomType().getId()).isEqualTo(newRoomType.getId());
        assertThat(saved.getRoomTypeNameSnapshot()).isEqualTo(newRoomType.getName());
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidStayDatesWithoutChangingStoredBooking() {
        Long bookingId = existingBooking.getId();

        assertThatThrownBy(() -> bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        newRoomType.getId(),
                        LocalDate.of(2027, 6, 13),
                        LocalDate.of(2027, 6, 13))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        Booking unchanged = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(unchanged.getCheckInDate()).isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(unchanged.getCheckOutDate()).isEqualTo(LocalDate.of(2027, 6, 13));
        assertThat(unchanged.getRoomType().getId()).isEqualTo(originalRoomType.getId());
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsCheckInAfterCheckOutWithoutChangingStoredBooking() {
        Long bookingId = existingBooking.getId();

        assertThatThrownBy(() -> bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        newRoomType.getId(),
                        LocalDate.of(2027, 6, 20),
                        LocalDate.of(2027, 6, 15))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        Booking unchanged = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(unchanged.getCheckInDate()).isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(unchanged.getCheckOutDate()).isEqualTo(LocalDate.of(2027, 6, 13));
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsNullRoomTypeIdWhenUpdatingBooking() {
        Long bookingId = existingBooking.getId();

        assertThatThrownBy(() -> bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        null,
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 5))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsNonExistentBookingId() {
        assertThatThrownBy(() -> bookingService.updateBooking(
                999999L,
                new BookingUpdateRequest(
                        newRoomType.getId(),
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 5))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsNonExistentRoomTypeId() {
        Long bookingId = existingBooking.getId();

        assertThatThrownBy(() -> bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        999999L,
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 5))))
                .isInstanceOf(com.ttcs.homestay.exception.RoomTypeNotFoundException.class);
    }

    @Test
    void verifiesAllBookingStatePreservedExceptUpdatedFields() {
        Long bookingId = existingBooking.getId();

        BookingResponse response = bookingService.updateBooking(
                bookingId,
                new BookingUpdateRequest(
                        newRoomType.getId(),
                        LocalDate.of(2027, 9, 1),
                        LocalDate.of(2027, 9, 6)));

        Booking saved = bookingRepository.findById(bookingId).orElseThrow();

        // Đảm bảo thông tin cập nhật chính xác
        assertThat(saved.getCheckInDate()).isEqualTo(LocalDate.of(2027, 9, 1));
        assertThat(saved.getCheckOutDate()).isEqualTo(LocalDate.of(2027, 9, 6));
        assertThat(saved.getRoomType().getId()).isEqualTo(newRoomType.getId());
        assertThat(saved.getRoomTypeNameSnapshot()).isEqualTo(newRoomType.getName());

        // Đảm bảo thông tin cũ và state không bị biến đổi, không tạo mới
        assertThat(saved.getId()).isEqualTo(bookingId);
        assertThat(saved.getBookingCode()).isEqualTo("UPDATEBK01");
        assertThat(saved.getGuestName()).isEqualTo("Khách cập nhật");
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        assertThat(saved.getTotalAmount()).isEqualTo(1_500_000L);
        assertThat(saved.getWeekdayPriceSnapshot()).isEqualTo(500_000L);
        assertThat(saved.getWeekendPriceSnapshot()).isEqualTo(700_000L);
        assertThat(saved.getWeekendDaysSnapshot()).isEqualTo("FRIDAY,SATURDAY");
        assertThat(bookingRepository.count()).isEqualTo(1);
    }
}
