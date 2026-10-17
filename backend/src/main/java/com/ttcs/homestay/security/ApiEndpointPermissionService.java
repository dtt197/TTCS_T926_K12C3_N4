package com.ttcs.homestay.security;

import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Service
public class ApiEndpointPermissionService {

    private static final List<String> INTERNAL_ROLES = List.of(
            "ADMIN", "OWNER", "RECEPTIONIST", "HOUSEKEEPING"
    );
    private static final List<RequestMethod> SUPPORTED_METHODS = List.of(
            RequestMethod.GET,
            RequestMethod.HEAD,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE,
            RequestMethod.OPTIONS,
            RequestMethod.TRACE
    );

    private final RequestMappingHandlerMapping handlerMapping;
    private final ApiSecurityPolicyCatalog policyCatalog;

    public ApiEndpointPermissionService(
            // S4-10: actuator thêm controllerEndpointHandlerMapping cùng loại, nên chỉ rõ bộ quản lý API chính.
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            ApiSecurityPolicyCatalog policyCatalog
    ) {
        this.handlerMapping = handlerMapping;
        this.policyCatalog = policyCatalog;
    }

    public List<EndpointPermission> getEndpointPermissions() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> endpointPermissions(mapping).stream())
                .sorted(Comparator.comparing((EndpointPermission endpoint) -> endpoint.path())
                        .thenComparing((EndpointPermission endpoint) -> endpoint.method()))
                .toList();
    }

    private List<EndpointPermission> endpointPermissions(RequestMappingInfo mapping) {
        List<String> paths = mapping.getPatternValues().stream()
                .filter(path -> path.equals("/api") || path.startsWith("/api/"))
                .toList();
        if (paths.isEmpty()) {
            return List.of();
        }

        List<RequestMethod> methods = mapping.getMethodsCondition().getMethods().isEmpty()
                ? SUPPORTED_METHODS
                : mapping.getMethodsCondition().getMethods().stream().toList();

        return paths.stream()
                .flatMap(path -> methods.stream().map(method -> {
                    var policy = policyCatalog.findPolicy(method.name(), path);
                    List<String> roles = switch (policy.access()) {
                        case PUBLIC -> List.of();
                        case AUTHENTICATED -> INTERNAL_ROLES;
                        case ROLES -> policy.roles();
                        case DENIED -> List.of();
                    };
                    return new EndpointPermission(
                            method.name(),
                            path,
                            policy.access().name(),
                            roles
                    );
                }))
                .toList();
    }

    public record EndpointPermission(
            String method,
            String path,
            String access,
            List<String> roles
    ) {
        public EndpointPermission {
            roles = List.copyOf(roles);
        }
    }
}
