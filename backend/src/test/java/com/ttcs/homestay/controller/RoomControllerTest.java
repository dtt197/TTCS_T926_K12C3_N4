package com.ttcs.homestay.controller;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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