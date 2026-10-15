package com.ttcs.homestay.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ttcs.homestay.service.SampleDataService;
import com.ttcs.homestay.service.SampleDataService.SampleDataSummary;

/**
 * S4-09: API nạp lại dữ liệu mẫu.
 * Chỉ OWNER mới được gọi (xem ApiSecurityPolicyCatalog).
 */
@RestController
@RequestMapping("/api/admin/sample-data")
public class SampleDataController {

    private final SampleDataService sampleDataService;

    public SampleDataController(SampleDataService sampleDataService) {
        this.sampleDataService = sampleDataService;
    }

    /**
     * POST /api/admin/sample-data/reload
     * Nạp lại toàn bộ dữ liệu mẫu về trạng thái ban đầu.
     */
    @PostMapping("/reload")
    public ResponseEntity<SampleDataSummary> reload() {
        SampleDataSummary summary = sampleDataService.reload();
        return ResponseEntity.ok(summary);
    }
}
