package com.ttcs.homestay.controller.security;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ttcs.homestay.security.ApiEndpointPermissionService;
import com.ttcs.homestay.security.ApiEndpointPermissionService.EndpointPermission;

@RestController
public class SecurityEndpointPermissionController {

    private final ApiEndpointPermissionService endpointPermissionService;

    public SecurityEndpointPermissionController(
            ApiEndpointPermissionService endpointPermissionService
    ) {
        this.endpointPermissionService = endpointPermissionService;
    }

    @GetMapping("/api/admin/security/endpoint-permissions")
    public List<EndpointPermission> getEndpointPermissions() {
        return endpointPermissionService.getEndpointPermissions();
    }
}
