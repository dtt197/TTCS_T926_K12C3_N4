package com.ttcs.homestay.controller.pricing;
import com.ttcs.homestay.dto.pricing.PriceOverridePreviewRequest;
import com.ttcs.homestay.dto.pricing.PriceOverridePreviewResponse;
import com.ttcs.homestay.dto.pricing.PriceOverrideRequest;
import com.ttcs.homestay.dto.pricing.PriceOverrideResponse;
import com.ttcs.homestay.service.AuditLogService;
import com.ttcs.homestay.service.PriceOverrideService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S2-02 Lát 1: API giá đè theo mùa hoặc ngày lễ. Quyền khai báo trong SecurityConfig
 * (ma trận "Bảng giá và chính sách huỷ"): xem cho Chủ homestay, Quản trị, Lễ tân; thêm/sửa/xoá chỉ Chủ homestay.
 */
@RestController
@RequestMapping("/api/price-overrides")
public class PriceOverrideController {

    private final PriceOverrideService priceOverrideService;
    private final AuditLogService auditLogService;

    public PriceOverrideController(PriceOverrideService priceOverrideService, AuditLogService auditLogService) {
        this.priceOverrideService = priceOverrideService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<PriceOverrideResponse> listPriceOverrides() {
        return priceOverrideService.listPriceOverrides();
    }

    @PostMapping
    public ResponseEntity<PriceOverrideResponse> createPriceOverride(
            @Valid @RequestBody PriceOverrideRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        PriceOverrideResponse response =
                priceOverrideService.createPriceOverride(request, jwt.getClaimAsString("fullName"));
        audit(jwt, httpRequest, "PRICE_OVERRIDE_CREATED");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
        /** S2-02 Lát 4: xem trước giá từng đêm của đợt đang nhập, không lưu gì nên không ghi nhật ký. */
    @PostMapping("/preview")
    public PriceOverridePreviewResponse previewPriceOverride(@Valid @RequestBody PriceOverridePreviewRequest request) {
        return priceOverrideService.preview(request);
    }

    @PutMapping("/{id}")
    public PriceOverrideResponse updatePriceOverride(
            @PathVariable Long id,
            @Valid @RequestBody PriceOverrideRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        PriceOverrideResponse response = priceOverrideService.updatePriceOverride(id, request);
        audit(jwt, httpRequest, "PRICE_OVERRIDE_UPDATED");
        return response;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePriceOverride(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        priceOverrideService.deletePriceOverride(id);
        audit(jwt, httpRequest, "PRICE_OVERRIDE_DELETED");
        return ResponseEntity.noContent().build();
    }

    /** Ghi nhật ký thao tác nghiệp vụ, giống các API khác của nhóm (S1-05). */
    private void audit(Jwt jwt, HttpServletRequest httpRequest, String action) {
        Long userId = Long.valueOf(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        auditLogService.recordSensitiveAction(userId, email, userId, email, action, httpRequest.getRemoteAddr());
    }
}