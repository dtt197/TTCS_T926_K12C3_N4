package com.ttcs.homestay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ttcs.homestay.dto.RoomResponse;
import com.ttcs.homestay.controller.room.RoomController;
import com.ttcs.homestay.dto.UpdateRoomStatusRequest;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.service.AuditLogService;
import com.ttcs.homestay.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class RoomControllerTest {

    private final RoomService roomService = mock(RoomService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final HttpServletRequest httpRequest = mock(HttpServletRequest.class);

    private final RoomController controller =
            new RoomController(roomService, auditLogService);

    @Test
    void updateStatusUsesAuthenticatedOperatorName() {
        JwtAuthenticationToken authentication = authentication(
                "Nguyễn Văn A",
                "letan@homestay.local",
                "RECEPTIONIST"
        );

        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        controller.updateStatus(
                1L,
                new UpdateRoomStatusRequest(RoomStatus.TRONG_SACH),
                authentication,
                httpRequest
        );

        verify(roomService).updateStatus(
                1L,
                RoomStatus.TRONG_SACH,
                "Nguyễn Văn A"
        );
    }

    @Test
    void checkOutUsesAuthenticatedOperatorName() {
        JwtAuthenticationToken authentication = authentication(
                "Nguyễn Văn A",
                "letan@homestay.local",
                "RECEPTIONIST"
        );

        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        controller.checkOut(
                1L,
                authentication,
                httpRequest
        );

        verify(roomService).checkOut(
                1L,
                "Nguyễn Văn A"
        );
    }

    @Test
void housekeepingCanOnlyMarkDirtyRoomAsClean() {
    JwtAuthenticationToken authentication = authentication(
            "Buồng phòng",
            "buongphong@homestay.local",
            "HOUSEKEEPING"
    );

    RoomResponse dirtyRoom = new RoomResponse(
            1L,
            "101",
            1,
            "Phòng đôi",
            RoomStatus.TRONG_BAN,
            true
    );

    when(roomService.getRooms())
            .thenReturn(List.of(dirtyRoom));

    when(httpRequest.getRemoteAddr())
            .thenReturn("127.0.0.1");

    controller.updateStatus(
            1L,
            new UpdateRoomStatusRequest(RoomStatus.TRONG_SACH),
            authentication,
            httpRequest
    );

    verify(roomService).updateStatus(
            1L,
            RoomStatus.TRONG_SACH,
            "Buồng phòng"
    );
}

    @Test
    void housekeepingGetRooms_onlyReturnsDirtyRooms_andPrioritizesGuestCheckInToday() {
        JwtAuthenticationToken authentication = authentication(
                "Buồng phòng",
                "buongphong@homestay.local",
                "HOUSEKEEPING"
        );

        RoomResponse cleanRoom = new RoomResponse(
                1L, "101", 1, "Phòng đôi", RoomStatus.TRONG_SACH, true
        );
        RoomResponse dirtyNoGuest = new RoomResponse(
                2L, "102", 1, "Phòng đôi", RoomStatus.TRONG_BAN, true, null, null, null, false, null
        );
        RoomResponse dirtyWithGuest = new RoomResponse(
                3L, "105", 1, "Phòng đôi", RoomStatus.TRONG_BAN, true, null, null, null, true, "14:00"
        );
        RoomResponse occupiedRoom = new RoomResponse(
                4L, "201", 2, "Phòng gia đình", RoomStatus.DANG_O, true
        );
        RoomResponse maintenanceRoom = new RoomResponse(
                5L, "202", 2, "Phòng đơn", RoomStatus.BAO_TRI, true
        );

        when(roomService.getRooms())
                .thenReturn(List.of(cleanRoom, dirtyNoGuest, dirtyWithGuest, occupiedRoom, maintenanceRoom));

        List<RoomResponse> result = controller.getRooms(authentication);

        // S3-09 Test 1: Chỉ hiển thị phòng trống bẩn
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(room -> room.status() == RoomStatus.TRONG_BAN);

        // S3-09 Test 2: Phòng có khách nhận trong ngày đứng trước
        assertThat(result.get(0).roomNumber()).isEqualTo("105");
        assertThat(result.get(0).hasGuestCheckInToday()).isTrue();
        assertThat(result.get(0).expectedCheckInTime()).isEqualTo("14:00");

        assertThat(result.get(1).roomNumber()).isEqualTo("102");
        assertThat(result.get(1).hasGuestCheckInToday()).isFalse();
    }

    @Test
    void housekeepingCannotMarkNonDirtyRoom_throwsForbidden() {
        JwtAuthenticationToken authentication = authentication(
                "Buồng phòng Demo",
                "buongphong@homestay.local",
                "HOUSEKEEPING"
        );

        RoomResponse occupiedRoom = new RoomResponse(
                1L,
                "101",
                1,
                "Phòng đôi",
                RoomStatus.DANG_O,
                true
        );

        when(roomService.getRooms())
                .thenReturn(List.of(occupiedRoom));

        assertThatThrownBy(() -> controller.updateStatus(
                1L,
                new UpdateRoomStatusRequest(RoomStatus.TRONG_SACH),
                authentication,
                httpRequest
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void housekeepingCannotChangeToNonCleanStatus_throwsForbidden() {
        JwtAuthenticationToken authentication = authentication(
                "Buồng phòng Demo",
                "buongphong@homestay.local",
                "HOUSEKEEPING"
        );

        RoomResponse dirtyRoom = new RoomResponse(
                1L,
                "101",
                1,
                "Phòng đôi",
                RoomStatus.TRONG_BAN,
                true
        );

        when(roomService.getRooms())
                .thenReturn(List.of(dirtyRoom));

        assertThatThrownBy(() -> controller.updateStatus(
                1L,
                new UpdateRoomStatusRequest(RoomStatus.BAO_TRI),
                authentication,
                httpRequest
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void housekeepingCleanedRoom_disappearsFromDirtyRoomsList() {
        JwtAuthenticationToken authentication = authentication(
                "Buồng phòng Demo",
                "buongphong@homestay.local",
                "HOUSEKEEPING"
        );

        // Ban đầu có 2 phòng trống bẩn: 101 và 102
        RoomResponse dirty101 = new RoomResponse(1L, "101", 1, "Phòng đôi", RoomStatus.TRONG_BAN, true);
        RoomResponse dirty102 = new RoomResponse(2L, "102", 1, "Phòng đôi", RoomStatus.TRONG_BAN, true);

        when(roomService.getRooms()).thenReturn(List.of(dirty101, dirty102));
        List<RoomResponse> dirtyListBefore = controller.getRooms(authentication);
        assertThat(dirtyListBefore).hasSize(2);

        // Sau khi phòng 101 được báo sạch (status chuyển sang TRONG_SACH)
        RoomResponse clean101 = new RoomResponse(1L, "101", 1, "Phòng đôi", RoomStatus.TRONG_SACH, true);
        when(roomService.getRooms()).thenReturn(List.of(clean101, dirty102));

        // Phòng 101 biến mất khỏi danh sách phòng của buồng phòng, chỉ còn lại phòng 102
        List<RoomResponse> dirtyListAfter = controller.getRooms(authentication);
        assertThat(dirtyListAfter).hasSize(1);
        assertThat(dirtyListAfter.get(0).roomNumber()).isEqualTo("102");
    }


    private JwtAuthenticationToken authentication(
            String fullName,
            String email,
            String role) {

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("1")
                .claim("fullName", fullName)
                .claim("email", email)
                .claim("role", role)
                .build();

        return new JwtAuthenticationToken(
                jwt,
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + role)
                )
        );
    }
}