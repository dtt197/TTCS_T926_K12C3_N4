package com.ttcs.homestay.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ttcs.homestay.entity.EndpointDiscrepancyTicket;

public interface EndpointDiscrepancyTicketRepository
        extends JpaRepository<EndpointDiscrepancyTicket, Long> {

    @EntityGraph(attributePaths = {"assignee", "assignee.role", "createdBy"})
    List<EndpointDiscrepancyTicket> findAllByOrderByCreatedAtDescIdDesc();
}
