package com.ttcs.homestay.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutPaymentRequest(
        @NotBlank String previewToken,
        String paymentMethod,
        String paymentReference,
        Boolean amountReceivedConfirmed,
        Boolean refundCompletedConfirmed) {}
