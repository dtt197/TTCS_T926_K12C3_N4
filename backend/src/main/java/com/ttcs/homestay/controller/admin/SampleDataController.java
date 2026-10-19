package com.ttcs.homestay.controller.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ttcs.homestay.service.SampleDataService;
import com.ttcs.homestay.service.SampleDataService.SampleDataSummary;

/**
 * S4-09: API nạp lại dữ liệu mẫu.
 * Chỉ OWNER mới được gọi (xem ApiSecurityPolicyCatalog) và chỉ khi bật chế độ demo (app.demo-mode=true),
 * vì thao tác này xoá toàn bộ loại phòng, phòng và booking hiện có.
 */
@RestController
@RequestMapping("/api/admin/sample-data")
public class SampleDataController {

    private final SampleDataService sampleDataService;
    private final boolean demoMode;

    public SampleDataController(SampleDataService sampleDataService,
            @Value("${app.demo-mode:false}") boolean demoMode) {
        this.sampleDataService = sampleDataService;
        this.demoMode = demoMode;
    }

    /**
     * POST /api/admin/sample-data/reload
     * Nạp lại toàn bộ dữ liệu mẫu về trạng thái ban đầu.
     */
    @PostMapping("/reload")
    public ResponseEntity<SampleDataSummary> reload() {
        if (!demoMode) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ nạp lại dữ liệu mẫu được trên môi trường staging đang bật chế độ demo (DEMO_MODE=true).");
        }
        SampleDataSummary summary = sampleDataService.reload();
        return ResponseEntity.ok(summary);
    }
}