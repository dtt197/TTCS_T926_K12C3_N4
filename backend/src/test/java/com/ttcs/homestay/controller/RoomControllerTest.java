package com.ttcs.homestay.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ttcs.homestay.dto.UpdateRoomStatusRequest;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.service.RoomService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** S1-10 AC5: lịch sử trạng thái ghi đúng người đang đăng nhập. */
class RoomControllerTest {

    private final RoomService roomService = mock(RoomService.class);
    private final RoomController roomController = new RoomController(roomService);

    @Test
    void doiTrangThai_ghiNguoiThaoTacLaNguoiDangNhap() {
        roomController.updateStatus(1L, new UpdateRoomStatusRequest(RoomStatus.TRONG_SACH),
                token("Nguyễn Lễ Tân", "RECEPTIONIST"));

        verify(roomService).updateStatus(1L, RoomStatus.TRONG_SACH, "Nguyễn Lễ Tân");
    }

    @Test
    void traPhong_ghiNguoiThaoTacLaNguoiDangNhap() {
        roomController.checkOut(2L, token("Demo Administrator", "ADMIN"));

        verify(roomService).checkOut(2L, "Demo Administrator");
    }

    @Test
    void tokenKhongCoHoTen_dungEmailLamNguoiThaoTac() {
        roomController.updateStatus(1L, new UpdateRoomStatusRequest(RoomStatus.TRONG_BAN),
                token(null, "RECEPTIONIST"));

        verify(roomService).updateStatus(1L, RoomStatus.TRONG_BAN, "letan@test.local");
    }

    private static JwtAuthenticationToken token(String fullName, String role) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("7")
                .claim("email", "letan@test.local")
                .claim("role", role);
        if (fullName != null) {
            builder.claim("fullName", fullName);
        }
        return new JwtAuthenticationToken(builder.build(), List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}