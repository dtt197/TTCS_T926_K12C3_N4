package com.ttcs.homestay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.RoomType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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
                .extracting(Booking::getBookingCode)
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
}