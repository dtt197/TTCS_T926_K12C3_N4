import test from 'node:test'
import assert from 'node:assert/strict'
import {
  isUndoNoShowEligible,
  formatRemainingTime,
  bookingActionsFor,
} from '../src/pages/bookingActionVisibility.ts'

test('SCRUM-151: Lễ tân hủy đánh dấu không đến trong vòng 15 phút (Undo No-show)', async (t) => {
  await t.test('Kịch bản 1: Hoàn tác trong vòng 15 phút -> eligible = true, tính đúng remainingSeconds', () => {
    const markedAt = '2026-10-15T14:35:00+07:00'
    // Sau khi đánh dấu 5 phút (14:40) -> còn 10 phút (600 giây)
    const currentTime = new Date('2026-10-15T14:40:00+07:00')

    const result = isUndoNoShowEligible(markedAt, 15, currentTime)
    assert.equal(result.eligible, true)
    assert.equal(result.remainingSeconds, 600)
    assert.equal(result.reason, undefined)
  })

  await t.test('Kịch bản 2: Hoàn tác ở phút thứ 16 -> eligible = false, remainingSeconds = 0, báo quá hạn', () => {
    const markedAt = '2026-10-15T14:35:00+07:00'
    // Sau khi đánh dấu 16 phút (14:51) -> quá hạn 15 phút
    const currentTime = new Date('2026-10-15T14:51:00+07:00')

    const result = isUndoNoShowEligible(markedAt, 15, currentTime)
    assert.equal(result.eligible, false)
    assert.equal(result.remainingSeconds, 0)
    assert.match(result.reason, /15 phút/)
  })

  await t.test('Kịch bản 3: Định dạng đồng hồ đếm ngược formatRemainingTime', () => {
    assert.equal(formatRemainingTime(900), '15:00')
    assert.equal(formatRemainingTime(605), '10:05')
    assert.equal(formatRemainingTime(59), '00:59')
    assert.equal(formatRemainingTime(0), '00:00')
    assert.equal(formatRemainingTime(-5), '00:00')
  })

  await t.test('Kịch bản 4: bookingActionsFor hiển thị action undo-no-show khi status là NO_SHOW và có quyền', () => {
    const bookingNoShow = {
      id: 105,
      bookingCode: 'BK-000105',
      guestName: 'Nguyen Van B',
      checkInDate: '2026-10-15',
      checkOutDate: '2026-10-16',
      totalAmount: 1000000,
      roomTypeId: 1,
      roomTypeNameSnapshot: 'Deluxe',
      status: 'NO_SHOW',
      holdExpired: false,
      roomNumber: null,
    }

    const actions = bookingActionsFor(
      bookingNoShow,
      { manage: true, cancel: true, noShow: true },
      false,
    )

    assert.ok(actions.includes('undo-no-show'), 'Phải bao gồm action undo-no-show')

    const bookingConfirmed = {
      ...bookingNoShow,
      status: 'DA_XAC_NHAN',
    }
    const actionsConfirmed = bookingActionsFor(
      bookingConfirmed,
      { manage: true, cancel: true, noShow: true },
      false,
    )
    assert.ok(!actionsConfirmed.includes('undo-no-show'), 'Không được có action undo-no-show khi chưa no-show')
  })
})
