package com.ttcs.homestay.controller.settings;

import com.ttcs.homestay.dto.settings.OperatingSettingsRequest;
import com.ttcs.homestay.dto.settings.OperatingSettingsResponse;
import com.ttcs.homestay.service.AuditLogService;
import com.ttcs.homestay.service.OperatingSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S1-09: API thông tin homestay và tham số vận hành.
 * Quyền khai báo trong SecurityConfig:
 * xem cho Chủ homestay, Quản trị, Lễ tân;
 * lịch sử cho Chủ homestay, Quản trị;
 * sửa chỉ Chủ homestay.
 */
@RestController
@RequestMapping("/api/settings")
public class OperatingSettingsController {

    private final OperatingSettingsService settingsService;
    private final AuditLogService auditLogService;

    public OperatingSettingsController(
            OperatingSettingsService settingsService,
            AuditLogService auditLogService) {
        this.settingsService = settingsService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public OperatingSettingsResponse getCurrent() {
        return settingsService.getCurrent();
    }

    /** AC4: lịch sử thay đổi (người sửa, thời điểm sửa). */
    @GetMapping("/history")
    public List<OperatingSettingsResponse> getHistory() {
        return settingsService.getHistory();
    }

    /** AC4: lưu thì tạo phiên bản mới, ghi người đang đăng nhập là người sửa. */
    @PutMapping
    public OperatingSettingsResponse update(
            @Valid @RequestBody OperatingSettingsRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {

        Long userId = Long.valueOf(jwt.getSubject());
        String fullName = jwt.getClaimAsString("fullName");
        String email = jwt.getClaimAsString("email");
        String ipAddress = httpRequest.getRemoteAddr();

        OperatingSettingsResponse response =
                settingsService.update(request, userId, fullName);

        auditLogService.recordSensitiveAction(
                userId,
                email,
                userId,
                email,
                "SETTINGS_UPDATED",
                ipAddress
        );

        return response;
    }
}