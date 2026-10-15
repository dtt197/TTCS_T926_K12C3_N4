package com.ttcs.homestay.controller.security;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ttcs.homestay.dto.security.CreateEndpointDiscrepancyRequest;
import com.ttcs.homestay.dto.security.EndpointDiscrepancyResponse;
import com.ttcs.homestay.service.EndpointDiscrepancyTicketService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/security/endpoint-discrepancies")
public class EndpointDiscrepancyTicketController {

    private final EndpointDiscrepancyTicketService ticketService;

    public EndpointDiscrepancyTicketController(EndpointDiscrepancyTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public List<EndpointDiscrepancyResponse> list() {
        return ticketService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointDiscrepancyResponse create(
            @Valid @RequestBody CreateEndpointDiscrepancyRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ticketService.create(request, Long.valueOf(jwt.getSubject()));
    }
}
