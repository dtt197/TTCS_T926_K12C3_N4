package com.ttcs.homestay.dto.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VendorRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 30) @Pattern(regexp = "[+0-9() .-]{6,30}") String phone,
        @Size(max = 2000) String notes) {}
