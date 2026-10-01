package com.ttcs.homestay.controller.room;

import com.ttcs.homestay.dto.roomtype.RoomTypeRequest;
import com.ttcs.homestay.dto.roomtype.RoomTypeResponse;
import com.ttcs.homestay.dto.roomtype.UpdateRoomTypeStatusRequest;
import com.ttcs.homestay.service.AuditLogService;
import com.ttcs.homestay.service.RoomTypeService;
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
@RequestMapping("/api/room-types")
public class RoomTypeController {

    private final RoomTypeService roomTypeService;
    private final AuditLogService auditLogService;

    public RoomTypeController(
            RoomTypeService roomTypeService,
            AuditLogService auditLogService) {
        this.roomTypeService = roomTypeService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<RoomTypeResponse> listRoomTypes() {
        return roomTypeService.listRoomTypes();
    }

    @PostMapping
    public ResponseEntity<RoomTypeResponse> createRoomType(
            @Valid @RequestBody RoomTypeRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        RoomTypeResponse response =
                roomTypeService.createRoomType(request);

        recordAction(
                authentication,
                "Loại phòng: " + response.name(),
                "ROOM_TYPE_CREATED",
                httpRequest
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{id}")
    public RoomTypeResponse updateRoomType(
            @PathVariable Long id,
            @Valid @RequestBody RoomTypeRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        RoomTypeResponse response =
                roomTypeService.updateRoomType(id, request);

        recordAction(
                authentication,
                "Loại phòng: " + response.name(),
                "ROOM_TYPE_UPDATED",
                httpRequest
        );

        return response;
    }

    @PatchMapping("/{id}/status")
    public RoomTypeResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoomTypeStatusRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        RoomTypeResponse response =
                roomTypeService.updateStatus(id, request.active());

        recordAction(
                authentication,
                "Loại phòng: " + response.name(),
                "ROOM_TYPE_STATUS_CHANGED",
                httpRequest
        );

        return response;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoomType(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        RoomTypeResponse beforeDelete =
                roomTypeService.listRoomTypes()
                        .stream()
                        .filter(roomType -> roomType.id().equals(id))
                        .findFirst()
                        .orElse(null);

        roomTypeService.deleteRoomType(id);

        recordAction(
                authentication,
                beforeDelete != null
                        ? "Loại phòng: " + beforeDelete.name()
                        : "Loại phòng #" + id,
                "ROOM_TYPE_DELETED",
                httpRequest
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/amenities/{amenityId}")
    public RoomTypeResponse addAmenity(
            @PathVariable Long id,
            @PathVariable Long amenityId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        RoomTypeResponse response =
                roomTypeService.addAmenity(id, amenityId);

        recordAction(
                authentication,
                "Loại phòng: " + response.name(),
                "ROOM_TYPE_AMENITY_ADDED",
                httpRequest
        );

        return response;
    }

    @DeleteMapping("/{id}/amenities/{amenityId}")
    public RoomTypeResponse removeAmenity(
            @PathVariable Long id,
            @PathVariable Long amenityId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        RoomTypeResponse response =
                roomTypeService.removeAmenity(id, amenityId);

        recordAction(
                authentication,
                "Loại phòng: " + response.name(),
                "ROOM_TYPE_AMENITY_REMOVED",
                httpRequest
        );

        return response;
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