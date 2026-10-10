package com.ttcs.homestay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
/**
 * S2-07 Lát 1: câu truy vấn booking trùng ngày chạy trên CSDL thật (H2), mỗi test tự rollback.
 * Có booking đã xác nhận, đã huỷ và đã trả phòng cùng ngày để kiểm tra trạng thái chiếm phòng.
 */
@SpringBootTest
@Transactional
class BookingRepositoryTest {

    private static final Set<BookingStatus> OCCUPYING =
            Set.of(BookingStatus.CHO_XAC_NHAN, BookingStatus.DA_XAC_NHAN, BookingStatus.DA_NHAN_PHONG);

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    private RoomType doi;

    @BeforeEach
    void setUp() {
        doi = new RoomType();
        doi.setCode("TEST_DOI_BK");
        doi.setName("Phòng đôi (test booking)");
        doi.setStandardCapacity(2);
        doi.setMaxCapacity(3);
        doi.setNumberOfBeds(1);
        doi = roomTypeRepository.save(doi);

        bookingRepository.save(booking("TESTBK01", BookingStatus.DA_XAC_NHAN));
        bookingRepository.save(booking("TESTBK02", BookingStatus.DA_HUY));
        bookingRepository.save(booking("TESTBK03", BookingStatus.DA_TRA_PHONG));
    }

    private Booking booking(String code, BookingStatus status) {
        Booking booking = new Booking();
        booking.setBookingCode(code);
        booking.setGuestName("Khách test");
        booking.setStatus(status);
        booking.setRoomType(doi);
        booking.setRoomTypeNameSnapshot(doi.getName());
        booking.setCheckInDate(LocalDate.of(2027, 6, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 6, 13));
        booking.setWeekdayPriceSnapshot(500_000L);
        booking.setWeekendPriceSnapshot(700_000L);
        booking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        booking.setTotalAmount(1_500_000L);
        booking.setCreatedAt(OffsetDateTime.now());
        return booking;
    }

    @Test
    void trungMotDem_chiBookingDangChiemPhongDuocTraVe() {
        assertThat(bookingRepository.findOverlapping(doi.getId(),
                LocalDate.of(2027, 6, 12), LocalDate.of(2027, 6, 15), OCCUPYING))
                .extracting(savedBooking -> savedBooking.getBookingCode())
                .containsExactly("TESTBK01");
    }

    @Test
    void khoangNuaMo_traPhongNgay13VaNhanPhongNgay13_khongTrung() {
        assertThat(bookingRepository.findOverlapping(doi.getId(),
                LocalDate.of(2027, 6, 13), LocalDate.of(2027, 6, 15), OCCUPYING)).isEmpty();
        assertThat(bookingRepository.findOverlapping(doi.getId(),
                LocalDate.of(2027, 6, 8), LocalDate.of(2027, 6, 10), OCCUPYING)).isEmpty();
    }

    @Test
    void maBookingDaCo_existsTraVeTrue() {
        assertThat(bookingRepository.existsByBookingCode("TESTBK01")).isTrue();
        assertThat(bookingRepository.existsByBookingCode("CHUACOMA")).isFalse();
    }

    @Test
    void findCheckInOptions_onlyReturnsConfirmedRoomAssignedBookingsWithCleanRooms() {
        Room cleanRoom = room("CHECKIN-CLEAN", RoomStatus.TRONG_SACH);
        Room anotherCleanRoom = room("CHECKIN-OTHER", RoomStatus.TRONG_SACH);
        Room dirtyRoom = room("CHECKIN-DIRTY", RoomStatus.TRONG_BAN);
        roomRepository.saveAll(List.of(cleanRoom, anotherCleanRoom, dirtyRoom));

        Booking eligible = booking("CHECKIN-ELIGIBLE", BookingStatus.DA_XAC_NHAN);
        eligible.setRoom(cleanRoom);
        eligible.setRoomConfirmedAt(OffsetDateTime.now());
        bookingRepository.save(eligible);

        Booking pending = booking("CHECKIN-PENDING", BookingStatus.CHO_XAC_NHAN);
        pending.setRoom(anotherCleanRoom);
        pending.setRoomConfirmedAt(OffsetDateTime.now());
        bookingRepository.save(pending);

        Booking dirty = booking("CHECKIN-DIRTY-ROOM", BookingStatus.DA_XAC_NHAN);
        dirty.setRoom(dirtyRoom);
        dirty.setRoomConfirmedAt(OffsetDateTime.now());
        bookingRepository.save(dirty);

        assertThat(bookingRepository.findCheckInOptions(
                BookingStatus.DA_XAC_NHAN, RoomStatus.TRONG_SACH))
                .extracting(found -> found.getBookingCode())
                .contains("CHECKIN-ELIGIBLE")
                .doesNotContain("CHECKIN-PENDING", "CHECKIN-DIRTY-ROOM");
    }

    private Room room(String roomNumber, RoomStatus status) {
        Room room = new Room();
        room.setRoomNumber(roomNumber);
        room.setFloor(1);
        room.setRoomType(doi.getName());
        room.setStatus(status);
        room.setActive(true);
        return room;
    }

    @Test
void searchTheoTrangThaiVaTuKhoaSoDienThoai() {
    Booking booking = booking("FILTER-01", BookingStatus.CHO_XAC_NHAN);
    booking.setGuestName("Nguyễn Văn Test");
    booking.setGuestPhone("0912345678");
    booking.setCheckInDate(LocalDate.of(2026, 10, 10));
    booking.setCheckOutDate(LocalDate.of(2026, 10, 12));
    bookingRepository.save(booking);

    Pageable pageable = PageRequest.of(
            0,
            20,
            Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            )
    );

    Page<Booking> result = bookingRepository.search(
            BookingStatus.CHO_XAC_NHAN,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31),
            "0912345678",
            pageable
    );

    assertThat(result.getContent())
            .extracting(foundBooking -> foundBooking.getBookingCode())
            .contains("FILTER-01");
}
}