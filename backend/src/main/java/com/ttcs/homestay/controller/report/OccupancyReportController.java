package com.ttcs.homestay.controller.report.legacy;

import com.ttcs.homestay.dto.OccupancyReportResponse;
import com.ttcs.homestay.service.OccupancyReportService;

public class OccupancyReportController {
    private final OccupancyReportService service;

    public OccupancyReportController(OccupancyReportService service) { this.service = service; }

}
