package com.ttcs.homestay.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutPaymentRequest(
        @NotBlank String previewToken,
        String paymentMethod,
        String paymentReference,
        Boolean amountReceivedConfirmed,
        Boolean refundCompletedConfirmed,
        String earlyCheckoutReason) {
    public CheckoutPaymentRequest(String previewToken, String paymentMethod, String paymentReference,
            Boolean amountReceivedConfirmed, Boolean refundCompletedConfirmed) {
        this(previewToken, paymentMethod, paymentReference, amountReceivedConfirmed, refundCompletedConfirmed, null);
    }
}
