package com.ttcs.homestay.controller;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.RoomTypeCancellationTier;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S2-04: Kiểm thử tích hợp tiêu chí:
 * "Khách biết rõ thời gian nhận phòng, trả phòng và các chính sách áp dụng cho loại phòng."
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicRoomDetailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private OperatingSettingsRepository operatingSettingsRepository;

    private RoomType standardRoomType;
    private RoomType customPolicyRoomType;

    @BeforeEach
    void setUp() {
        // Đảm bảo OperatingSettings luôn tồn tại cho môi trường test
        OperatingSettings settings = operatingSettingsRepository.findAll().stream().findFirst().orElseGet(() -> {
            OperatingSettings s = new OperatingSettings();
            s.setHomestayName("HomeStay Test");
            s.setCheckInTime(LocalTime.of(14, 0));
            s.setCheckOutTime(LocalTime.of(12, 0));
            s.setLateCheckoutFeePerHour(100000L);
            s.setExtraPersonFee(200000L);
            s.setCreatedByName("Hệ thống");
            s.setCreatedAt(OffsetDateTime.now());
            s.setWeekendDays("FRIDAY,SATURDAY");
            s.addCancellationTier(72, 100);
            s.addCancellationTier(24, 50);
            s.addCancellationTier(0, 0);
            return operatingSettingsRepository.save(s);
        });

        if (settings.getCancellationTiers().isEmpty()) {
            settings.addCancellationTier(72, 100);
            settings.addCancellationTier(24, 50);
            settings.addCancellationTier(0, 0);
            operatingSettingsRepository.save(settings);
        }

        // Loại phòng 1: Phòng Tiêu Chuẩn (Thừa hưởng / Cấu hình chuẩn)
        standardRoomType = roomTypeRepository.findByCodeIgnoreCase("DON").orElseGet(() -> {
            RoomType rt = new RoomType();
            rt.setCode("DON");
            rt.setName("Phòng đơn");
            rt.setStandardCapacity(1);
            rt.setMaxCapacity(2);
            rt.setNumberOfBeds(1);
            rt.setDescription("Phòng đơn ấm cúng");
            rt.setStatus(true);
            rt.setWeekdayPrice(300000L);
            rt.setWeekendPrice(400000L);
            return roomTypeRepository.save(rt);
        });

        standardRoomType.setStatus(true);
        standardRoomType.setCheckInTime(LocalTime.of(14, 0));
        standardRoomType.setCheckOutTime(LocalTime.of(12, 0));
        standardRoomType.setAllowChildren(true);
        standardRoomType.setChildPolicy("Cho phép mang theo trẻ nhỏ. Trẻ dưới 6 tuổi ở cùng người lớn miễn phí.");
        standardRoomType.setExtraPersonFee(150000L);
        standardRoomType.setCancellationPolicy("Chính sách hủy linh hoạt chuẩn homestay");
        standardRoomType = roomTypeRepository.save(standardRoomType);

        // Loại phòng 2: Phòng Studio Người Lớn (Cấu hình chính sách riêng biệt để test tính linh hoạt)
        customPolicyRoomType = roomTypeRepository.findByCodeIgnoreCase("STUDIO_ADULT").orElseGet(() -> {
            RoomType rt = new RoomType();
            rt.setCode("STUDIO_ADULT");
            rt.setName("Studio Người Lớn");
            rt.setStandardCapacity(2);
            rt.setMaxCapacity(2);
            rt.setNumberOfBeds(1);
            rt.setDescription("Không gian yên tĩnh dành cho người lớn");
            rt.setStatus(true);
            rt.setWeekdayPrice(700000L);
            rt.setWeekendPrice(900000L);
            return roomTypeRepository.save(rt);
        });

        customPolicyRoomType.setStatus(true);
        customPolicyRoomType.setCheckInTime(LocalTime.of(15, 0));
        customPolicyRoomType.setCheckOutTime(LocalTime.of(11, 0));
        customPolicyRoomType.setAllowChildren(false);
        customPolicyRoomType.setChildPolicy("Không cho phép mang theo trẻ nhỏ để đảm bảo không gian nghỉ dưỡng yên tĩnh tuyệt đối.");
        customPolicyRoomType.setExtraPersonFee(300000L);
        customPolicyRoomType.setCancellationPolicy("Chính sách hủy nghiêm ngặt dành riêng cho phòng Studio");
        
        if (customPolicyRoomType.getCancellationTiers().isEmpty()) {
            customPolicyRoomType.addCancellationTier(48, 80);
            customPolicyRoomType.addCancellationTier(0, 0);
        }
        customPolicyRoomType = roomTypeRepository.save(customPolicyRoomType);
    }

    @Test
    @DisplayName("Kịch bản 1: Kiểm tra hiển thị đúng Giờ nhận phòng và Giờ trả phòng")
    void kiemTraHienThiDungGioNhanPhongVaTraPhong() throws Exception {
        mockMvc.perform(get("/api/public/room-types/" + standardRoomType.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(standardRoomType.getId()))
                .andExpect(jsonPath("$.checkInTime").value("14:00"))
                .andExpect(jsonPath("$.checkOutTime").value("12:00"));
    }

    @Test
    @DisplayName("Kịch bản 2: Kiểm tra hiển thị đúng Chính sách trẻ nhỏ")
    void kiemTraHienThiDungChinhSachTreNho() throws Exception {
        mockMvc.perform(get("/api/public/room-types/" + standardRoomType.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowChildren").value(true))
                .andExpect(jsonPath("$.childPolicy").value("Cho phép mang theo trẻ nhỏ. Trẻ dưới 6 tuổi ở cùng người lớn miễn phí."));
    }

    @Test
    @DisplayName("Kịch bản 3: Kiểm tra hiển thị Phí hủy phòng và mốc thời gian áp dụng chính xác")
    void kiemTraHienThiPhiHuyPhongVaMocThoiGianApDung() throws Exception {
        mockMvc.perform(get("/api/public/room-types/" + standardRoomType.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraPersonFee").value(150000))
                .andExpect(jsonPath("$.cancellationPolicy").value("Chính sách hủy linh hoạt chuẩn homestay"))
                .andExpect(jsonPath("$.cancellationTiers").isArray())
                .andExpect(jsonPath("$.cancellationTiers", hasSize(greaterThan(0))))
                // Mốc trước 72h: hoàn 100%, phí phạt 0% (miễn phí)
                .andExpect(jsonPath("$.cancellationTiers[0].hoursBeforeCheckIn").value(72))
                .andExpect(jsonPath("$.cancellationTiers[0].refundPercent").value(100))
                .andExpect(jsonPath("$.cancellationTiers[0].feePercent").value(0))
                .andExpect(jsonPath("$.cancellationTiers[0].feeDescription").value("Miễn phí hủy (hoàn trả 100% tiền cọc)"))
                // Mốc trước 24h: hoàn 50%, phí phạt 50%
                .andExpect(jsonPath("$.cancellationTiers[1].hoursBeforeCheckIn").value(24))
                .andExpect(jsonPath("$.cancellationTiers[1].refundPercent").value(50))
                .andExpect(jsonPath("$.cancellationTiers[1].feePercent").value(50))
                .andExpect(jsonPath("$.cancellationTiers[1].feeDescription").value("Phí phạt 50% tiền cọc (hoàn lại 50%)"))
                // Mốc sát ngày 0h: hoàn 0%, phí phạt 100%
                .andExpect(jsonPath("$.cancellationTiers[2].hoursBeforeCheckIn").value(0))
                .andExpect(jsonPath("$.cancellationTiers[2].refundPercent").value(0))
                .andExpect(jsonPath("$.cancellationTiers[2].feePercent").value(100))
                .andExpect(jsonPath("$.cancellationTiers[2].feeDescription").value("Phí phạt 100% (không hoàn tiền cọc)"));
    }

    @Test
    @DisplayName("Kịch bản 4: Kiểm tra tính linh hoạt - Các loại phòng khác nhau cấu hình và hiển thị chính sách/phí hủy khác nhau")
    void kiemTraTinhLinhHoatGiuaCacLoaiPhong() throws Exception {
        // Loại phòng 1: Phòng Tiêu Chuẩn (14:00 - 12:00, Cho phép trẻ, phụ thu 150.000, chính sách hủy 3 mốc)
        mockMvc.perform(get("/api/public/room-types/" + standardRoomType.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInTime").value("14:00"))
                .andExpect(jsonPath("$.checkOutTime").value("12:00"))
                .andExpect(jsonPath("$.allowChildren").value(true))
                .andExpect(jsonPath("$.extraPersonFee").value(150000))
                .andExpect(jsonPath("$.cancellationTiers", hasSize(3)));

        // Loại phòng 2: Studio Người Lớn (15:00 - 11:00, Không cho phép trẻ, phụ thu 300.000, chính sách riêng 2 mốc)
        mockMvc.perform(get("/api/public/room-types/" + customPolicyRoomType.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInTime").value("15:00"))
                .andExpect(jsonPath("$.checkOutTime").value("11:00"))
                .andExpect(jsonPath("$.allowChildren").value(false))
                .andExpect(jsonPath("$.childPolicy").value("Không cho phép mang theo trẻ nhỏ để đảm bảo không gian nghỉ dưỡng yên tĩnh tuyệt đối."))
                .andExpect(jsonPath("$.extraPersonFee").value(300000))
                .andExpect(jsonPath("$.cancellationPolicy").value("Chính sách hủy nghiêm ngặt dành riêng cho phòng Studio"))
                .andExpect(jsonPath("$.cancellationTiers", hasSize(2)))
                .andExpect(jsonPath("$.cancellationTiers[0].hoursBeforeCheckIn").value(48))
                .andExpect(jsonPath("$.cancellationTiers[0].refundPercent").value(80))
                .andExpect(jsonPath("$.cancellationTiers[0].feePercent").value(20))
                .andExpect(jsonPath("$.cancellationTiers[1].hoursBeforeCheckIn").value(0))
                .andExpect(jsonPath("$.cancellationTiers[1].refundPercent").value(0))
                .andExpect(jsonPath("$.cancellationTiers[1].feePercent").value(100));
    }

    @Test
    @DisplayName("Edge Case: Truy cập loại phòng không tồn tại trả về 404")
    void khachXemLoaiPhongKhongTonTai_traVe404() throws Exception {
        mockMvc.perform(get("/api/public/room-types/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy thông tin loại phòng #999999"));
    }

    @Test
    @DisplayName("Khách xem danh mục loại phòng trả về danh sách kèm thông tin chính sách")
    void khachXemDanhMucLoaiPhong_traVeDanhSachKemChinhSach() throws Exception {
        mockMvc.perform(get("/api/public/room-types-catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].checkInTime").exists())
                .andExpect(jsonPath("$[0].checkOutTime").exists())
                .andExpect(jsonPath("$[0].cancellationTiers").isArray());
    }
}
