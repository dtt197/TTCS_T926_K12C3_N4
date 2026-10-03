package com.ttcs.homestay.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.RoomTypeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S2-04: Kiểm thử tích hợp cho API công khai xem chi tiết loại phòng và danh mục phòng.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicRoomDetailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    private RoomType testRoomType;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        testRoomType = roomTypeRepository.findAll().stream().findFirst().orElseGet(() -> {
            RoomType rt = new RoomType();
            rt.setCode("TEST_ROOM");
            rt.setName("Phòng Test Deluxe");
            rt.setStandardCapacity(2);
            rt.setMaxCapacity(3);
            rt.setNumberOfBeds(1);
            rt.setDescription("Phòng sang trọng view thành phố");
            return roomTypeRepository.save(rt);
        });
        testRoomType.setStatus(true);
        testRoomType.setWeekdayPrice(350000L);
        testRoomType.setWeekendPrice(450000L);
        testRoomType = roomTypeRepository.save(testRoomType);
    }

    @Test
    @DisplayName("TC1 & TC2: Khách lấy chi tiết loại phòng tồn tại trả về đầy đủ ảnh, mô tả, tiện nghi, sức chứa, số phòng trống")
    void khachXemChiTietLoaiPhong_traVeDayDuThongTin() throws Exception {
        mockMvc.perform(get("/api/public/room-types/" + testRoomType.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testRoomType.getId()))
                .andExpect(jsonPath("$.name").value(testRoomType.getName()))
                .andExpect(jsonPath("$.code").value(testRoomType.getCode()))
                .andExpect(jsonPath("$.description").exists())
                .andExpect(jsonPath("$.maxCapacity").value(testRoomType.getMaxCapacity()))
                .andExpect(jsonPath("$.standardCapacity").value(testRoomType.getStandardCapacity()))
                .andExpect(jsonPath("$.availableRooms").isNumber())
                .andExpect(jsonPath("$.amenities").isArray())
                .andExpect(jsonPath("$.images").isArray())
                .andExpect(jsonPath("$.images.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("TC3: Edge Case - Truy cập loại phòng không tồn tại trả về 404 kèm thông báo lỗi phù hợp")
    void khachXemLoaiPhongKhongTonTai_traVe404() throws Exception {
        mockMvc.perform(get("/api/public/room-types/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy thông tin loại phòng #999999"));
    }

    @Test
    @DisplayName("Khách xem danh mục loại phòng công khai trả về danh sách đầy đủ chi tiết")
    void khachXemDanhMucLoaiPhong_traVeDanhSach() throws Exception {
        mockMvc.perform(get("/api/public/room-types-catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].images").isArray())
                .andExpect(jsonPath("$[0].maxCapacity").isNumber())
                .andExpect(jsonPath("$[0].availableRooms").isNumber());
    }
}
