package com.ttcs.homestay.controller.room;

import com.ttcs.homestay.dto.amenity.AmenityRequest;
import com.ttcs.homestay.dto.amenity.AmenityResponse;
import com.ttcs.homestay.dto.amenity.UpdateAmenityStatusRequest;
import com.ttcs.homestay.service.AmenityService;
import com.ttcs.homestay.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/amenities")
public class AmenityController {

    private final AmenityService amenityService;
    private final AuditLogService auditLogService;

    public AmenityController(
            AmenityService amenityService,
            AuditLogService auditLogService) {
        this.amenityService = amenityService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AmenityResponse> listAmenities() {
        return amenityService.listAmenities();
    }

    @PostMapping
    public ResponseEntity<AmenityResponse> createAmenity(
            @Valid @RequestBody AmenityRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        AmenityResponse response =
                amenityService.createAmenity(request);

        recordAction(
                authentication,
                "Tiện nghi: " + response.name(),
                "AMENITY_CREATED",
                httpRequest
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{id}")
    public AmenityResponse updateAmenity(
            @PathVariable Long id,
            @Valid @RequestBody AmenityRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        AmenityResponse response =
                amenityService.updateAmenity(id, request);

        recordAction(
                authentication,
                "Tiện nghi: " + response.name(),
                "AMENITY_UPDATED",
                httpRequest
        );

        return response;
    }

    @PatchMapping("/{id}/status")
    public AmenityResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAmenityStatusRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        AmenityResponse response =
                amenityService.updateStatus(id, request.active());

        recordAction(
                authentication,
                "Tiện nghi: " + response.name(),
                "AMENITY_STATUS_CHANGED",
                httpRequest
        );

        return response;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAmenity(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        AmenityResponse beforeDelete =
                amenityService.listAmenities()
                        .stream()
                        .filter(amenity -> amenity.id().equals(id))
                        .findFirst()
                        .orElse(null);

        amenityService.deleteAmenity(id);

        recordAction(
                authentication,
                beforeDelete != null
                        ? "Tiện nghi: " + beforeDelete.name()
                        : "Tiện nghi #" + id,
                "AMENITY_DELETED",
                httpRequest
        );

        return ResponseEntity.noContent().build();
    }

    private void recordAction(
            Authentication authentication,
            String target,
            String action,
            HttpServletRequest httpRequest) {

        auditLogService.recordSensitiveAction(
                actorUserId(authentication),
                actorEmail(authentication),
                null,
                target,
                action,
                httpRequest.getRemoteAddr()
        );
    }

    private static Long actorUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            try {
                return Long.valueOf(token.getToken().getSubject());
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        return null;
    }

    private static String actorEmail(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            String email =
                    token.getToken().getClaimAsString("email");

            if (email != null && !email.isBlank()) {
                return email;
            }
        }

        return authentication.getName();
    }
}