import test from 'node:test'
import assert from 'node:assert/strict'
import { isNoShowEligible, bookingActionsFor } from '../src/pages/bookingActionVisibility.ts'

test('SCRUM-149: Lễ tân đánh dấu khách không đến - Kiểm tra điều kiện thời gian và check-in', async (t) => {
  await t.test('Kịch bản 1: Bấm nút trước giờ hẹn 30 phút -> Bị khóa / ineligible', () => {
    const booking = {
      id: 101,
      bookingCode: 'BK-000101',
      checkInDate: '2026-10-15',
      status: 'DA_XAC_NHAN',
    }

    // Giờ nhận phòng tiêu chuẩn là 14:00. Lúc 14:15 mới qua 15 phút, chưa đủ 30 phút.
    const evaluationTime = new Date('2026-10-15T14:15:00+07:00')
    const result = isNoShowEligible(booking, evaluationTime, '14:00')

    assert.equal(result.eligible, false)
    assert.match(result.reason, /Chưa đủ điều kiện/)
  })

  await t.test('Kịch bản 2: Bấm nút sau giờ hẹn 30 phút khi đã check-in -> Bị khóa / ineligible', () => {
    const bookingCheckedIn = {
      id: 102,
      bookingCode: 'BK-000102',
      checkInDate: '2026-10-15',
      status: 'DA_NHAN_PHONG',
    }

    // Lúc 15:00 đã quá 30 phút, nhưng khách đã nhận phòng
    const evaluationTime = new Date('2026-10-15T15:00:00+07:00')
    const result = isNoShowEligible(bookingCheckedIn, evaluationTime, '14:00')

    assert.equal(result.eligible, false)
    assert.match(result.reason, /đã check-in/)
  })

  await t.test('Kịch bản 3: Bấm nút sau giờ hẹn 30 phút khi chưa check-in -> Thành công (eligible)', () => {
    const bookingConfirmed = {
      id: 103,
      bookingCode: 'BK-000103',
      checkInDate: '2026-10-15',
      status: 'DA_XAC_NHAN',
    }

    // Lúc 14:35 đã quá 30 phút kể từ 14:00 và khách chưa check-in
    const evaluationTime = new Date('2026-10-15T14:35:00+07:00')
    const result = isNoShowEligible(bookingConfirmed, evaluationTime, '14:00')

    assert.equal(result.eligible, true)
    assert.equal(result.reason, undefined)
  })

  await t.test('bookingActionsFor hiển thị action no-show khi có quyền', () => {
    const booking = {
      id: 104,
      bookingCode: 'BK-000104',
      guestName: 'Nguyen Van A',
      checkInDate: '2026-10-15',
      checkOutDate: '2026-10-16',
      totalAmount: 1000000,
      roomTypeId: 1,
      roomTypeNameSnapshot: 'Deluxe',
      status: 'DA_XAC_NHAN',
      holdExpired: false,
      roomNumber: '101',
    }

    const actions = bookingActionsFor(
      booking,
      { manage: true, cancel: true, noShow: true },
      false,
    )

    assert.ok(actions.includes('no-show'))
  })
})
