package com.ttcs.homestay.controller.room;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.RoomTypeImage;
import com.ttcs.homestay.repository.RoomTypeRepository;
import com.ttcs.homestay.repository.RoomTypeImageRepository;
import com.ttcs.homestay.service.RoomTypeService;
import jakarta.persistence.EntityManager;
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
    private RoomTypeImageRepository roomTypeImageRepository;

    @Autowired
    private RoomTypeService roomTypeService;

    @Autowired
    private EntityManager entityManager;

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
    void publicCardUsesPrimaryUploadedImageAndStoredAltText() throws Exception {
        RoomType roomType = saveRoomType("PUBLIC_COVER", "Room With Cover", true, 200_000L);
        roomType.setImageAlt("Alt cover đã lưu");
        roomTypeRepository.saveAndFlush(roomType);
        saveImage(roomType, "/uploads/first.jpg", "/uploads/first_thumb.jpg", 0, false);
        saveImage(roomType, "/uploads/cover.jpg", "/uploads/cover_thumb.jpg", 1, true);
        entityManager.clear();

        mockMvc.perform(get("/api/public/room-type-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imageUrl").value("/uploads/cover_thumb.jpg"))
                .andExpect(jsonPath("$[0].imageAlt").value("Alt cover đã lưu"));
    }

    @Test
    void publicCardUsesFirstOrderedImageWhenNoPrimaryIsMarked() throws Exception {
        RoomType roomType = saveRoomType("PUBLIC_FIRST", "Room With Ordered Images", true, 200_000L);
        saveImage(roomType, "/uploads/later.jpg", "/uploads/later_thumb.jpg", 1, false);
        saveImage(roomType, "/uploads/first.jpg", "/uploads/first_thumb.jpg", 0, false);
        entityManager.clear();

        mockMvc.perform(get("/api/public/room-type-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(roomType.getId()))
                .andExpect(jsonPath("$[0].imageUrl").value("/uploads/first_thumb.jpg"))
                .andExpect(jsonPath("$[0].imageAlt").value("Ảnh Room With Ordered Images"));
    }

    @Test
    void publicCardUsesLegacyImageAndAltWhenThereAreNoUploadedImages() throws Exception {
        RoomType roomType = saveRoomType("PUBLIC_LEGACY", "Legacy Room", true, 200_000L);
        roomType.setImageUrl("/room-images/room-2.jpg");
        roomType.setImageAlt("Alt legacy đã lưu");
        roomTypeRepository.saveAndFlush(roomType);
        entityManager.clear();

        mockMvc.perform(get("/api/public/room-type-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imageUrl").value("/room-images/room-2.jpg"))
                .andExpect(jsonPath("$[0].imageAlt").value("Alt legacy đã lưu"));
    }

    @Test
    void publicCardWithoutAnyImageReturnsNullUrlAndNonEmptyGeneratedAlt() throws Exception {
        RoomType roomType = saveRoomType("PUBLIC_NO_IMAGE", "Room Without Image", true, 200_000L);

        mockMvc.perform(get("/api/public/room-type-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(roomType.getId()))
                .andExpect(jsonPath("$[0].imageUrl").doesNotExist())
                .andExpect(jsonPath("$[0].imageAlt").value("Ảnh Room Without Image"));
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

    private void saveImage(
            RoomType roomType,
            String imageUrl,
            String thumbnailUrl,
            int displayOrder,
            boolean primary) {
        roomTypeImageRepository.saveAndFlush(
                new RoomTypeImage(roomType, imageUrl, thumbnailUrl, displayOrder, primary));
    }
}
