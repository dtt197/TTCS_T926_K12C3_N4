package com.ttcs.homestay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvoiceCancellationRequest(
        @NotBlank @Size(max = 500) String reason) {
}
