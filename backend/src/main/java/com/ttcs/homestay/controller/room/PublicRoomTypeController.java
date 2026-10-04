package com.ttcs.homestay.controller.room;

import com.ttcs.homestay.dto.roomtype.PublicRoomTypeCard;
import com.ttcs.homestay.service.PublicRoomTypeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** S2-03: API công khai, khách không cần đăng nhập (khai báo trong SecurityConfig). */
@RestController
@RequestMapping("/api/public")
public class PublicRoomTypeController {

    private final PublicRoomTypeService publicRoomTypeService;

    public PublicRoomTypeController(PublicRoomTypeService publicRoomTypeService) {
        this.publicRoomTypeService = publicRoomTypeService;
    }

    @GetMapping("/room-type-cards")
    public List<PublicRoomTypeCard> listRoomTypeCards() {
        return publicRoomTypeService.listRoomTypes();
    }
}