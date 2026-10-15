package com.ttcs.homestay.dto.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MaintenanceNoteRequest(@NotBlank @Size(max = 2000) String notes) {}
