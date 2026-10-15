import type { CheckoutPaymentRequest, CheckoutPreview } from './roomService'

export const EARLY_CHECKOUT_REASON_REQUIRED = 'Nhập lý do trả phòng sớm trước khi xác nhận.'
export const EARLY_CHECKOUT_REASON_TOO_LONG = 'Lý do trả phòng sớm không được vượt quá 500 ký tự.'

export function getEarlyCheckoutReasonError(earlyCheckout: boolean, reason: string): string | null {
  if (!earlyCheckout) return null
  const normalized = reason.trim()
  if (!normalized) return EARLY_CHECKOUT_REASON_REQUIRED
  if (normalized.length > 500) return EARLY_CHECKOUT_REASON_TOO_LONG
  return null
}

export function buildCheckoutPaymentRequest(
  preview: Pick<CheckoutPreview, 'previewToken' | 'settlementType' | 'earlyCheckout'>,
  payment: Pick<CheckoutPaymentRequest, 'paymentMethod' | 'paymentReference' | 'amountReceivedConfirmed' | 'refundCompletedConfirmed'>,
  earlyCheckoutReason = '',
): CheckoutPaymentRequest {
  return {
    previewToken: preview.previewToken,
    ...(preview.settlementType !== 'ZERO' ? payment : {}),
    ...(preview.earlyCheckout ? { earlyCheckoutReason: earlyCheckoutReason.trim() } : {}),
  }
}

export function getClosedBookingCheckoutDisplay(
  status: string,
  checkedOutAt: string | null | undefined,
  earlyCheckoutReason: string | null | undefined,
) {
  return status === 'DA_TRA_PHONG' ? {
    checkedOutAt: checkedOutAt ?? null,
    earlyCheckoutReason: earlyCheckoutReason?.trim() || null,
  } : null
}

export function getEarlyCheckoutSummary(preview: Pick<CheckoutPreview,
  'bookedNights' | 'usedNights' | 'unusedNights' | 'unusedRoomValue' | 'unusedRoomValueWarning' | 'settlementTotalBeforeDeposit'>) {
  return {
    bookedNights: preview.bookedNights,
    usedNights: preview.usedNights,
    unusedNights: preview.unusedNights,
    unusedRoomValue: preview.unusedRoomValue,
    unusedRoomValueWarning: preview.unusedRoomValueWarning,
    settlementTotalBeforeDeposit: preview.settlementTotalBeforeDeposit,
    warning: 'Các đêm chưa sử dụng vẫn được tính đủ tiền theo booking ban đầu.',
  }
}
