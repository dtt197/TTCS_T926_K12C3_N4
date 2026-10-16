package com.ttcs.homestay.service;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ttcs.homestay.dto.security.CreateEndpointDiscrepancyRequest;
import com.ttcs.homestay.dto.security.EndpointDiscrepancyResponse;
import com.ttcs.homestay.entity.EndpointDiscrepancyTicket;
import com.ttcs.homestay.repository.EndpointDiscrepancyTicketRepository;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.security.ApiEndpointPermissionService;
import com.ttcs.homestay.security.ApiEndpointPermissionService.EndpointPermission;

@Service
public class EndpointDiscrepancyTicketService {

    private static final Set<String> ALLOWED_ROLES =
            Set.of("ADMIN", "OWNER", "RECEPTIONIST", "HOUSEKEEPING");

    private final EndpointDiscrepancyTicketRepository tickets;
    private final ApiEndpointPermissionService endpointPermissions;
    private final UserRepository users;

    public EndpointDiscrepancyTicketService(
            EndpointDiscrepancyTicketRepository tickets,
            ApiEndpointPermissionService endpointPermissions,
            UserRepository users
    ) {
        this.tickets = tickets;
        this.endpointPermissions = endpointPermissions;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<EndpointDiscrepancyResponse> list() {
        return tickets.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(EndpointDiscrepancyResponse::from)
                .toList();
    }

    @Transactional
    public EndpointDiscrepancyResponse create(
            CreateEndpointDiscrepancyRequest request,
            Long reporterId
    ) {
        List<String> approvedRoles = normalizedRoles(request.approvedRoles());
        EndpointPermission actual = findEndpoint(request.method(), request.path());

        if (!ALLOWED_ROLES.containsAll(approvedRoles)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai tro duoc phe duyet khong hop le");
        }
        if (actual.access().equals(request.approvedAccess())
                && Set.copyOf(actual.roles()).equals(Set.copyOf(approvedRoles))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Endpoint khong con sai lech phan quyen");
        }

        var assignee = users.findWithRoleById(request.assigneeId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Nguoi phu trach khong ton tai"));
        if (!assignee.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Nguoi phu trach phai co tai khoan dang hoat dong");
        }

        var reporter = users.findWithRoleById(reporterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Khong tim thay nguoi tao phieu"));
        if (!reporter.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tai khoan nguoi tao da bi vo hieu hoa");
        }

        EndpointDiscrepancyTicket ticket = new EndpointDiscrepancyTicket(
                actual.method(),
                actual.path(),
                request.approvedAccess(),
                approvedRoles,
                actual.access(),
                actual.roles(),
                request.description().trim(),
                assignee,
                reporter
        );
        return EndpointDiscrepancyResponse.from(tickets.save(ticket));
    }

    private EndpointPermission findEndpoint(String method, String path) {
        return endpointPermissions.getEndpointPermissions().stream()
                .filter(endpoint -> endpoint.method().equals(method))
                .filter(endpoint -> endpoint.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Khong tim thay endpoint API"));
    }

    private List<String> normalizedRoles(List<String> roles) {
        if (roles == null || roles.stream().distinct().count() != roles.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sach vai tro khong hop le");
        }
        return roles.stream().sorted().toList();
    }
}
