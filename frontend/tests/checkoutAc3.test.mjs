import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildCheckoutPaymentRequest,
  getClosedBookingCheckoutDisplay,
  getEarlyCheckoutReasonError,
  getEarlyCheckoutSummary,
} from '../src/services/checkoutAc3.ts'

const payment = {
  paymentMethod: 'BANK_TRANSFER',
  paymentReference: ' TX-42 ',
  amountReceivedConfirmed: true,
  refundCompletedConfirmed: undefined,
}

function preview(overrides = {}) {
  return {
    previewToken: 'token-1',
    settlementType: 'COLLECT',
    earlyCheckout: false,
    bookedNights: 4,
    usedNights: 2,
    unusedNights: 2,
    unusedRoomValue: 500000,
    unusedRoomValueWarning: null,
    settlementTotalBeforeDeposit: 1500000,
    ...overrides,
  }
}

test('đúng ngày checkout không yêu cầu lý do', () => {
  assert.equal(getEarlyCheckoutReasonError(false, ''), null)
  assert.deepEqual(buildCheckoutPaymentRequest(preview(), payment), {
    previewToken: 'token-1', ...payment,
  })
})

test('trả phòng sớm bắt buộc có lý do', () => {
  assert.equal(getEarlyCheckoutReasonError(true, ''), 'Nhập lý do trả phòng sớm trước khi xác nhận.')
})

test('lý do toàn khoảng trắng không được xác nhận', () => {
  assert.notEqual(getEarlyCheckoutReasonError(true, '  \t  '), null)
})

test('lý do hợp lệ được trim và gửi cùng token, thanh toán, mã giao dịch, xác nhận', () => {
  assert.equal(getEarlyCheckoutReasonError(true, '  Khách cần về sớm  '), null)
  assert.deepEqual(buildCheckoutPaymentRequest(preview({ earlyCheckout: true }), payment, '  Khách cần về sớm  '), {
    previewToken: 'token-1', ...payment, earlyCheckoutReason: 'Khách cần về sớm',
  })
})

test('hiển thị cảnh báo và số đêm chưa dùng mà không đổi tổng quyết toán', () => {
  const data = preview()
  const summary = getEarlyCheckoutSummary(data)
  assert.equal(summary.bookedNights, 4)
  assert.equal(summary.usedNights, 2)
  assert.equal(summary.unusedNights, 2)
  assert.equal(summary.unusedRoomValue, 500000)
  assert.equal(summary.warning, 'Các đêm chưa sử dụng vẫn được tính đủ tiền theo booking ban đầu.')
  assert.equal(summary.settlementTotalBeforeDeposit, 1500000)
  assert.equal(data.settlementTotalBeforeDeposit, 1500000)
})

test('booking đã đóng hiển thị thời điểm checkout và lý do, dữ liệu cũ thiếu trường vẫn an toàn', () => {
  assert.deepEqual(getClosedBookingCheckoutDisplay('DA_TRA_PHONG', '2026-10-12T13:00:00+07:00', '  Khách về sớm  '), {
    checkedOutAt: '2026-10-12T13:00:00+07:00',
    earlyCheckoutReason: 'Khách về sớm',
  })
  assert.deepEqual(getClosedBookingCheckoutDisplay('DA_TRA_PHONG', undefined, undefined), {
    checkedOutAt: null,
    earlyCheckoutReason: null,
  })
  assert.equal(getClosedBookingCheckoutDisplay('DA_NHAN_PHONG', null, null), null)
})

test('checkout thường giữ nguyên thông tin phí muộn, phụ thu, cọc và phương thức thanh toán trong preview', () => {
  const data = preview({ lateCheckoutFee: 200000, bookingSurchargeTotal: 75000, depositTotal: 300000 })
  const before = structuredClone(data)
  const request = buildCheckoutPaymentRequest(data, payment)
  assert.deepEqual(request, { previewToken: 'token-1', ...payment })
  assert.deepEqual(data, before)
})
