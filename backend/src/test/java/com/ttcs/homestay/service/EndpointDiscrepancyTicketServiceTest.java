package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ttcs.homestay.dto.security.CreateEndpointDiscrepancyRequest;
import com.ttcs.homestay.entity.EndpointDiscrepancyTicket;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.EndpointDiscrepancyTicketRepository;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.security.ApiEndpointPermissionService;

@ExtendWith(MockitoExtension.class)
class EndpointDiscrepancyTicketServiceTest {

    @Mock
    private EndpointDiscrepancyTicketRepository tickets;

    @Mock
    private ApiEndpointPermissionService endpointPermissions;

    @Mock
    private UserRepository users;

    private EndpointDiscrepancyTicketService service;

    @BeforeEach
    void setUp() {
        service = new EndpointDiscrepancyTicketService(tickets, endpointPermissions, users);
    }

    @Test
    void createsTicketWithEffectivePermissionsAndAssignedOwner() {
        ApiEndpointPermissionService.EndpointPermission endpoint =
                new ApiEndpointPermissionService.EndpointPermission(
                        "GET",
                        "/api/rooms/calendar",
                        "ROLES",
                        List.of("ADMIN", "RECEPTIONIST")
                );
        when(endpointPermissions.getEndpointPermissions()).thenReturn(List.of(endpoint));
        User assignee = assignee(42L, "Lễ tân phụ trách", "RECEPTIONIST");
        User reporter = mock(User.class);
        when(reporter.getFullName()).thenReturn("Quản trị");
        when(reporter.isActive()).thenReturn(true);
        when(users.findWithRoleById(42L)).thenReturn(Optional.of(assignee));
        when(users.findWithRoleById(7L)).thenReturn(Optional.of(reporter));
        when(tickets.save(any(EndpointDiscrepancyTicket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(
                new CreateEndpointDiscrepancyRequest(
                        "GET",
                        "/api/rooms/calendar",
                        "ROLES",
                        List.of("RECEPTIONIST"),
                        42L,
                        "Endpoint cấp quyền thừa cho ADMIN."
                ),
                7L
        );

        assertThat(response.method()).isEqualTo("GET");
        assertThat(response.approvedRoles()).containsExactly("RECEPTIONIST");
        assertThat(response.actualRoles()).containsExactly("ADMIN", "RECEPTIONIST");
        assertThat(response.assigneeId()).isEqualTo(42L);
        assertThat(response.assigneeName()).isEqualTo("Lễ tân phụ trách");
        assertThat(response.createdByName()).isEqualTo("Quản trị");
        assertThat(response.description()).contains("cấp quyền thừa");
    }

    @Test
    void refusesToCreateTicketForEndpointThatNoLongerDiffers() {
        when(endpointPermissions.getEndpointPermissions()).thenReturn(List.of(
                new ApiEndpointPermissionService.EndpointPermission(
                        "GET", "/api/rooms/calendar", "ROLES", List.of("RECEPTIONIST")
                )
        ));

        assertThatThrownBy(() -> service.create(
                new CreateEndpointDiscrepancyRequest(
                        "GET",
                        "/api/rooms/calendar",
                        "ROLES",
                        List.of("RECEPTIONIST"),
                        42L,
                        "Đã khắc phục."
                ),
                7L
        )).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(tickets, never()).save(any());
    }

    @Test
    void refusesInactiveAssignee() {
        when(endpointPermissions.getEndpointPermissions()).thenReturn(List.of(
                new ApiEndpointPermissionService.EndpointPermission(
                        "GET", "/api/rooms/calendar", "ROLES", List.of("ADMIN", "RECEPTIONIST")
                )
        ));
        User assignee = mock(User.class);
        when(assignee.isActive()).thenReturn(false);
        when(users.findWithRoleById(42L)).thenReturn(Optional.of(assignee));

        assertThatThrownBy(() -> service.create(
                new CreateEndpointDiscrepancyRequest(
                        "GET",
                        "/api/rooms/calendar",
                        "ROLES",
                        List.of("RECEPTIONIST"),
                        42L,
                        "Cần xử lý."
                ),
                7L
        )).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(tickets, never()).save(any());
    }

    private User assignee(Long id, String name, String roleCode) {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn(roleCode);
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getFullName()).thenReturn(name);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(true);
        return user;
    }
}
