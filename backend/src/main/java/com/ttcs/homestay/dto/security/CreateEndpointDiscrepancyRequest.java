package com.ttcs.homestay.dto.security;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEndpointDiscrepancyRequest(
        @NotBlank
        @Pattern(regexp = "GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS|TRACE")
        String method,
        @NotBlank
        @Size(max = 512)
        @Pattern(regexp = "^/api/.+")
        String path,
        @NotBlank
        @Pattern(regexp = "PUBLIC|AUTHENTICATED|ROLES|DENIED")
        String approvedAccess,
        @NotNull
        @Size(max = 4)
        List<@Pattern(regexp = "ADMIN|OWNER|RECEPTIONIST|HOUSEKEEPING") String> approvedRoles,
        @NotNull
        Long assigneeId,
        @NotBlank
        @Size(max = 2000)
        String description
) {
}
