package com.ttcs.homestay;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcs.homestay.dto.RoomCalendarResponse;
import com.ttcs.homestay.dto.RoomCalendarResponse.CellStatus;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.service.RoomCalendarService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * S3-10 Lát 4 (AC4): lưới 40 phòng × 14 ngày với nhiều booking phải tải dưới 2 giây,
 * chỉ đọc cơ sở dữ liệu hai lần (phòng, booking) và chỉ trả dữ liệu trong khoảng ngày đang xem.
 */
@SpringBootTest
@Transactional
class RoomCalendarPerformanceTest {

    private static final int ROOM_COUNT = 40;
    private static final int DAYS = 14;
    private static final int BOOKINGS_PER_ROOM = 4;

    @Autowired
    private RoomCalendarService roomCalendarService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void luoi40PhongTrong14NgayTaiDuoiHaiGiayVaChiDocDuLieuHaiLan() {
        String prefix = "K" + UUID.randomUUID().toString().substring(0, 6) + "-";
        LocalDate start = LocalDate.now().plusDays(30);
        List<Room> rooms = createRooms(prefix);
        int expectedBookedNights = createBookings(rooms, prefix, start);
        entityManager.flush();
        entityManager.clear();

        // Lần gọi đầu để làm nóng; lần thứ hai mới đo.
        roomCalendarService.getCalendar(start, DAYS);
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        boolean statisticsWereEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        long startedAt = System.nanoTime();
        RoomCalendarResponse calendar = roomCalendarService.getCalendar(start, DAYS);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        long statements = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(statisticsWereEnabled);

        List<RoomCalendarResponse.Row> rows = calendar.rooms().stream()
                .filter(row -> row.roomNumber().startsWith(prefix))
                .toList();
        List<RoomCalendarResponse.Cell> bookedCells = rows.stream()
                .flatMap(row -> row.cells().stream())
                .filter(cell -> cell.status() == CellStatus.BOOKED)
                .toList();

        System.out.printf("%n[S3-10] So do %d phong x %d ngay, %d booking: %d ms, %d lan doc du lieu, %d o co booking%n",
                ROOM_COUNT, DAYS, ROOM_COUNT * BOOKINGS_PER_ROOM, elapsedMillis, statements, bookedCells.size());

        assertThat(calendar.dates()).hasSize(DAYS);
        assertThat(calendar.endDateExclusive()).isEqualTo(start.plusDays(DAYS));
        assertThat(rows).hasSize(ROOM_COUNT);
        assertThat(rows).allSatisfy(row -> assertThat(row.cells()).hasSize(DAYS));
        assertThat(bookedCells)
                .as("Chỉ các đêm nằm trong 14 ngày đang xem mới được tô là có booking")
                .hasSize(expectedBookedNights);
        assertThat(bookedCells).allSatisfy(cell -> {
            assertThat(cell.bookingId()).isNotNull();
            assertThat(cell.guestName()).isNotBlank();
            assertThat(cell.bookingCode()).isNotBlank();
        });
        assertThat(statements)
                .as("Tải sơ đồ chỉ đọc dữ liệu hai lần: danh sách phòng và booking trong khoảng ngày")
                .isLessThanOrEqualTo(2);
        assertThat(elapsedMillis)
                .as("Lưới 40 phòng × 14 ngày phải tải xong dưới 2 giây")
                .isLessThan(2_000);
    }

    private List<Room> createRooms(String prefix) {
        List<Room> rooms = new ArrayList<>();
        for (int index = 1; index <= ROOM_COUNT; index++) {
            Room room = new Room();
            room.setRoomNumber(prefix + String.format("%02d", index));
            room.setFloor(9);
            room.setRoomType("Phòng hiệu năng " + prefix);
            room.setStatus(RoomStatus.TRONG_SACH);
            room.setActive(true);
            rooms.add(room);
        }
        return roomRepository.saveAll(rooms);
    }

    /** Mỗi phòng 4 booking 2–3 đêm rải trong 14 ngày, thêm booking ngoài khoảng ngày; trả về số đêm có booking trong 14 ngày. */
    private int createBookings(List<Room> rooms, String prefix, LocalDate start) {
        List<Booking> bookings = new ArrayList<>();
        int bookedNights = 0;
        for (int index = 0; index < rooms.size(); index++) {
            int roomIndex = index + 1;
            for (int block = 0; block < BOOKINGS_PER_ROOM; block++) {
                int offset = block * 4 + roomIndex % 3;
                int nights = 2 + roomIndex % 2;
                bookings.add(booking(rooms.get(index), prefix + roomIndex + "-" + block,
                        start.plusDays(offset), start.plusDays(offset + nights)));
                for (int day = offset; day < offset + nights; day++) {
                    if (day < DAYS) {
                        bookedNights++;
                    }
                }
            }
        }
        // Booking trả phòng đúng ngày bắt đầu sơ đồ: nằm ngoài khoảng ngày nên không được trả về.
        bookings.add(booking(rooms.get(0), prefix + "TRUOC", start.minusDays(3), start));
        bookingRepository.saveAll(bookings);
        return bookedNights;
    }

    private static Booking booking(Room room, String code, LocalDate checkIn, LocalDate checkOut) {
        Booking booking = new Booking();
        booking.setBookingCode(code);
        booking.setGuestName("Khách hiệu năng " + code);
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setRoom(room);
        booking.setRoomTypeNameSnapshot(room.getRoomType());
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkOut);
        booking.setWeekdayPriceSnapshot(500_000L);
        booking.setWeekendPriceSnapshot(700_000L);
        booking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        booking.setTotalAmount(1_000_000L);
        booking.setGuestCount(2);
        booking.setCreatedAt(OffsetDateTime.now());
        return booking;
    }
}