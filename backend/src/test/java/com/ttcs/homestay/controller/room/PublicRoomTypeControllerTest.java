package com.ttcs.homestay.controller.room;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.RoomTypeRepository;
import com.ttcs.homestay.service.RoomTypeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicRoomTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomTypeService roomTypeService;

    @BeforeEach
    void deactivateExistingRoomTypes() {
        List<RoomType> existingRoomTypes = roomTypeRepository.findAll();
        existingRoomTypes.forEach(roomType -> roomType.setStatus(false));
        roomTypeRepository.saveAll(existingRoomTypes);
    }

    @Test
    void publicListOnlyIncludesActiveRoomTypesAndPreservesDisplayOrder() throws Exception {
        RoomType expensiveZulu = saveRoomType("PUBLIC_ZULU", "Zulu", true, 300_000L);
        RoomType cheapest = saveRoomType("PUBLIC_CHEAP", "Cheapest", true, 200_000L);
        RoomType expensiveAlpha = saveRoomType("PUBLIC_ALPHA", "Alpha", true, 300_000L);
        RoomType inactive = saveRoomType("PUBLIC_INACTIVE", "Inactive", false, 100_000L);

        mockMvc.perform(get("/api/public/room-type-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", contains(
                        cheapest.getId().intValue(),
                        expensiveAlpha.getId().intValue(),
                        expensiveZulu.getId().intValue())))
                .andExpect(jsonPath("$[?(@.id == " + inactive.getId() + ")]").doesNotExist());
    }

    @Test
    void reactivatedRoomTypeAppearsAfterReload() throws Exception {
        RoomType active = saveRoomType("PUBLIC_ACTIVE", "Active", true, 200_000L);
        RoomType reactivated = saveRoomType("PUBLIC_REACTIVATED", "Reactivated", false, 100_000L);

        mockMvc.perform(get("/api/public/room-type-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(active.getId()));

        reactivated.setStatus(true);
        roomTypeRepository.saveAndFlush(reactivated);

        mockMvc.perform(get("/api/public/room-type-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", contains(
                        reactivated.getId().intValue(),
                        active.getId().intValue())));
    }

    @Test
    void publicListIsEmptyWhenAllRoomTypesAreInactive() throws Exception {
        saveRoomType("PUBLIC_INACTIVE_ONE", "Inactive One", false, 200_000L);
        saveRoomType("PUBLIC_INACTIVE_TWO", "Inactive Two", false, 300_000L);

        mockMvc.perform(get("/api/public/room-type-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void internalRoomTypeServiceStillListsInactiveRoomTypes() {
        RoomType active = saveRoomType("INTERNAL_ACTIVE", "Internal Active", true, 200_000L);
        RoomType inactive = saveRoomType("INTERNAL_INACTIVE", "Internal Inactive", false, 300_000L);

        org.assertj.core.api.Assertions.assertThat(roomTypeService.listRoomTypes())
                .extracting("id")
                .contains(active.getId(), inactive.getId());
    }

    private RoomType saveRoomType(String code, String name, boolean active, long price) {
        RoomType roomType = new RoomType();
        roomType.setCode(code);
        roomType.setName(name);
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(2);
        roomType.setNumberOfBeds(1);
        roomType.setWeekdayPrice(price);
        roomType.setWeekendPrice(price);
        roomType.setStatus(active);
        return roomTypeRepository.saveAndFlush(roomType);
    }
}
