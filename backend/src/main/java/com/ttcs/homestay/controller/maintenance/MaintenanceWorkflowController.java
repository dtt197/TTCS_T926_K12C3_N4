package com.ttcs.homestay.controller.maintenance;

import com.ttcs.homestay.dto.maintenance.MaintenanceResponse;
import com.ttcs.homestay.dto.maintenance.MaintenanceNoteRequest;
import com.ttcs.homestay.dto.maintenance.VendorRequest;
import com.ttcs.homestay.service.MaintenanceWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/maintenance-requests")
public class MaintenanceWorkflowController {
    private final MaintenanceWorkflowService service;

    public MaintenanceWorkflowController(MaintenanceWorkflowService service) { this.service = service; }

    @GetMapping
    public MaintenanceWorkflowService.RequestPage list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(jwt, page, size);
    }

    @GetMapping("/cleaning-room-ids")
    public java.util.List<Long> cleaningRoomIds(@AuthenticationPrincipal Jwt jwt) {
        return service.cleaningRoomIds(jwt);
    }

    @GetMapping("/{id}")
    public MaintenanceWorkflowService.Detail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.detail(jwt, id);
    }

    @PostMapping("/{id}/accept")
    public MaintenanceResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            HttpServletRequest request) {
        return service.accept(jwt, id, request.getRemoteAddr());
    }

    @PutMapping("/{id}/vendor")
    public MaintenanceResponse vendor(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody VendorRequest vendor, HttpServletRequest request) {
        return service.updateVendor(jwt, id, vendor, request.getRemoteAddr());
    }

    @PostMapping("/{id}/start")
    public MaintenanceResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody VendorRequest vendor, HttpServletRequest request) {
        return service.start(jwt, id, vendor, request.getRemoteAddr());
    }

    @PostMapping("/{id}/repair-completed")
    public MaintenanceResponse repairCompleted(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody MaintenanceNoteRequest body, HttpServletRequest request) {
        return service.repairCompleted(jwt, id, body, request.getRemoteAddr());
    }

    @PostMapping("/{id}/verify")
    public MaintenanceResponse verify(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestParam boolean passed, @Valid @RequestBody MaintenanceNoteRequest body, HttpServletRequest request) {
        return service.verify(jwt, id, passed, body, request.getRemoteAddr());
    }

    @PostMapping("/{id}/cleaning-completed")
    public MaintenanceResponse cleaningCompleted(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody MaintenanceNoteRequest body, HttpServletRequest request) {
        return service.cleaningCompleted(jwt, id, body, request.getRemoteAddr());
    }
}
