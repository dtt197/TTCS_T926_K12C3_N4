package com.ttcs.homestay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.service.RoomStatusPolicy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RoomStatusPolicyTest {

    @Test
    void onlyCleanEmptyRoomCanCheckIn() {
        assertTrue(RoomStatusPolicy.canCheckIn(RoomStatus.TRONG_SACH));
        assertFalse(RoomStatusPolicy.canCheckIn(RoomStatus.TRONG_BAN));
        assertFalse(RoomStatusPolicy.canCheckIn(RoomStatus.DANG_O));
        assertFalse(RoomStatusPolicy.canCheckIn(RoomStatus.BAO_TRI));
    }

    @Test
    void occupiedRoomCannotMoveDirectlyToMaintenance() {
        assertFalse(RoomStatusPolicy.canChangeTo(RoomStatus.DANG_O, RoomStatus.BAO_TRI));
        assertTrue(RoomStatusPolicy.canChangeTo(RoomStatus.TRONG_SACH, RoomStatus.BAO_TRI));
        assertTrue(RoomStatusPolicy.canChangeTo(RoomStatus.TRONG_BAN, RoomStatus.TRONG_SACH));
    }

    @Test
    void maintenancePeriodMustHaveEndDateOnOrAfterStartDate() {
        assertTrue(RoomStatusPolicy.hasValidMaintenancePeriod(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)));
        assertTrue(RoomStatusPolicy.hasValidMaintenancePeriod(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)));
        assertFalse(RoomStatusPolicy.hasValidMaintenancePeriod(
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1)));
        assertFalse(RoomStatusPolicy.hasValidMaintenancePeriod(null, LocalDate.of(2026, 9, 1)));
    }
}