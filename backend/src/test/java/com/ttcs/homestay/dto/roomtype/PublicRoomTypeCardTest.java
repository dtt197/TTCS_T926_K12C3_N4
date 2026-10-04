package com.ttcs.homestay.dto.roomtype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PublicRoomTypeCardTest {

    @Test
    void lowestPrice_lay_gia_thap_hon_khi_co_ca_hai() {
        assertEquals(450000L, PublicRoomTypeCard.lowestPrice(450000L, 550000L));
    }

    @Test
    void lowestPrice_lay_gia_con_lai_khi_thieu_mot_gia() {
        assertEquals(550000L, PublicRoomTypeCard.lowestPrice(null, 550000L));
        assertEquals(450000L, PublicRoomTypeCard.lowestPrice(450000L, 0L));
    }

    @Test
    void lowestPrice_tra_null_khi_chua_co_gia() {
        assertNull(PublicRoomTypeCard.lowestPrice(null, null));
        assertNull(PublicRoomTypeCard.lowestPrice(0L, 0L));
    }
}