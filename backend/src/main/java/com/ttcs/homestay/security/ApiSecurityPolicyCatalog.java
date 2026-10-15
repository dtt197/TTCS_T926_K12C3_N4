package com.ttcs.homestay.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

public class ApiSecurityPolicyCatalog {

    public enum Access {
        PUBLIC,
        AUTHENTICATED,
        ROLES,
        DENIED
    }

    public record Policy(
            String method,
            String path,
            Access access,
            List<String> roles
    ) {
        public Policy {
            roles = List.copyOf(roles);
        }
    }

    private static final List<String> INTERNAL_ROLES = List.of(
            "ADMIN", "OWNER", "RECEPTIONIST", "HOUSEKEEPING"
    );
    private static final List<String> ADMIN_OWNER_RECEPTION = List.of(
            "ADMIN", "OWNER", "RECEPTIONIST"
    );
    private static final List<Policy> POLICIES = createPolicies();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public List<Policy> policies() {
        return POLICIES;
    }

    public Policy findPolicy(String method, String path) {
        return POLICIES.stream()
                .filter(policy -> matchesMethod(policy.method(), method))
                .filter(policy -> pathMatcher.match(policy.path(), path))
                .findFirst()
                .orElseGet(() -> new Policy(
                        method,
                        path,
                        Access.DENIED,
                        List.of()
                ));
    }

    private boolean matchesMethod(String policyMethod, String requestMethod) {
        if (policyMethod == null) {
            return true;
        }
        if (requestMethod == null || requestMethod.isBlank()) {
            return false;
        }
        return HttpMethod.valueOf(policyMethod.trim().toUpperCase())
                == HttpMethod.valueOf(requestMethod.trim().toUpperCase());
    }

    private static List<Policy> createPolicies() {
        List<Policy> policies = new ArrayList<>();

        add(policies, null, Access.PUBLIC, List.of(),
                "/api/auth/login",
                "/api/auth/refresh",
                "/api/auth/logout",
                "/api/auth/forgot-password",
                "/api/auth/reset-password");
        add(policies, null, Access.AUTHENTICATED, List.of(), "/api/internal/**");
        add(policies, "GET", Access.ROLES, List.of("HOUSEKEEPING"),
                "/api/maintenance-requests/cleaning-room-ids");
        add(policies, "GET", Access.ROLES,
                List.of("OWNER", "RECEPTIONIST", "HOUSEKEEPING"),
                "/api/maintenance-requests",
                "/api/maintenance-requests/*");
        add(policies, "POST", Access.ROLES, List.of("OWNER"),
                "/api/maintenance-requests/*/accept",
                "/api/maintenance-requests/*/start",
                "/api/maintenance-requests/*/repair-completed",
                "/api/maintenance-requests/*/verify");
        add(policies, "POST", Access.ROLES, List.of("HOUSEKEEPING"),
                "/api/maintenance-requests/*/cleaning-completed");
        add(policies, "PUT", Access.ROLES, List.of("OWNER"),
                "/api/maintenance-requests/*/vendor");
        add(policies, "GET", Access.ROLES, List.of("OWNER", "RECEPTIONIST"),
                "/api/notifications",
                "/api/notifications/stream",
                "/api/notifications/unread-count");
        add(policies, "PATCH", Access.ROLES, List.of("OWNER", "RECEPTIONIST"),
                "/api/notifications/*/read");
        add(policies, null, Access.ROLES, List.of("ADMIN"),
                "/api/admin/audit-logs/**");
        add(policies, "GET", Access.ROLES, List.of("ADMIN", "OWNER"),
                "/api/admin/users");
        add(policies, "GET", Access.ROLES, List.of("ADMIN"),
                "/api/admin/security/endpoint-permissions",
                "/api/admin/security/endpoint-discrepancies");
        add(policies, "POST", Access.ROLES, List.of("ADMIN"),
                "/api/admin/security/endpoint-discrepancies");
        add(policies, null, Access.ROLES, List.of("ADMIN"), "/api/admin/**");
        add(policies, "GET", Access.ROLES, List.of("OWNER"),
                "/api/reports/occupancy",
                "/api/reports/occupancy.csv",
                "/api/reports/occupancy/detailed");
        add(policies, null, Access.AUTHENTICATED, List.of(), "/api/account/**");
        add(policies, "GET", Access.ROLES, List.of("RECEPTIONIST"),
                "/api/rooms/calendar",
                "/api/rooms/*/checkout-preview");
        add(policies, "GET", Access.ROLES, INTERNAL_ROLES,
                "/api/rooms", "/api/rooms/**");
        add(policies, "PATCH", Access.ROLES, List.of("RECEPTIONIST", "HOUSEKEEPING"),
                "/api/rooms/*/status");
        add(policies, "PATCH", Access.ROLES, List.of("RECEPTIONIST"),
                "/api/rooms/*/maintenance");
        add(policies, "PATCH", Access.ROLES, List.of("HOUSEKEEPING"),
                "/api/rooms/*/incident");
        add(policies, "POST", Access.ROLES, List.of("RECEPTIONIST"),
                "/api/rooms/*/check-in", "/api/rooms/*/check-out");
        add(policies, null, Access.ROLES, List.of("ADMIN", "OWNER"),
                "/api/room-management/**");
        add(policies, "GET", Access.ROLES, INTERNAL_ROLES,
                "/api/room-types", "/api/room-types/**");
        add(policies, null, Access.ROLES, List.of("ADMIN", "OWNER"),
                "/api/room-types/**");
        add(policies, "GET", Access.ROLES, INTERNAL_ROLES,
                "/api/amenities", "/api/amenities/**");
        add(policies, null, Access.ROLES, List.of("ADMIN", "OWNER"),
                "/api/amenities/**");
        add(policies, "GET", Access.ROLES, List.of("OWNER", "ADMIN"),
                "/api/settings/history");
        add(policies, "GET", Access.ROLES,
                List.of("OWNER", "ADMIN", "RECEPTIONIST"), "/api/settings");
        add(policies, "PUT", Access.ROLES, List.of("OWNER"), "/api/settings");
        add(policies, "GET", Access.ROLES,
                List.of("OWNER", "ADMIN", "RECEPTIONIST"),
                "/api/price-overrides", "/api/price-overrides/**");
        add(policies, null, Access.ROLES, List.of("OWNER"),
                "/api/price-overrides/**");
        add(policies, "POST", Access.ROLES, List.of("RECEPTIONIST"),
                "/api/bookings", "/api/bookings/*/preview",
                "/api/bookings/*/cancel");
        add(policies, "POST", Access.ROLES, List.of("OWNER", "RECEPTIONIST"),
                "/api/bookings/*/deposit-adjustments",
                "/api/bookings/*/surcharges");
        add(policies, "PUT", Access.ROLES, List.of("RECEPTIONIST"),
                "/api/bookings/*/confirm",
                "/api/bookings/*/room",
                "/api/bookings/*/room-change",
                "/api/bookings/*/cancel",
                "/api/bookings/*");
        add(policies, "GET", Access.ROLES, ADMIN_OWNER_RECEPTION,
                "/api/bookings/*/room-change-history",
                "/api/bookings/*/available-rooms");
        add(policies, "PUT", Access.ROLES, List.of("OWNER", "RECEPTIONIST"),
                "/api/bookings/*/deposit", "/api/bookings/*/deposits/**");
        add(policies, "DELETE", Access.ROLES, List.of("OWNER", "RECEPTIONIST"),
                "/api/bookings/*/deposit",
                "/api/bookings/*/deposits/**",
                "/api/bookings/*");
        add(policies, "GET", Access.ROLES, ADMIN_OWNER_RECEPTION,
                "/api/pricing/**");
        add(policies, "GET", Access.ROLES, ADMIN_OWNER_RECEPTION,
                "/api/bookings", "/api/bookings/**");
        add(policies, "GET", Access.PUBLIC, List.of(),
                "/api/public/room-types",
                "/api/public/room-types/*",
                "/api/public/room-types-catalog",
                "/api/public/quote",
                "/api/public/room-type-cards",
                "/api/public/rooms/search");
        add(policies, "POST", Access.PUBLIC, List.of(),
                "/api/public/bookings",
                "/api/public/bookings/lookup");
        add(policies, "POST", Access.ROLES, List.of("RECEPTIONIST"),
                "/api/public/bookings/walk-in");
        add(policies, null, Access.PUBLIC, List.of(), "/uploads/**");
        add(policies, null, Access.DENIED, List.of(), "/api", "/api/**");

        return List.copyOf(policies);
    }

    private static void add(
            List<Policy> policies,
            String method,
            Access access,
            List<String> roles,
            String... paths
    ) {
        for (String path : paths) {
            policies.add(new Policy(method, path, access, roles));
        }
    }
}
