import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createRoomCalendarPlan, mergeRoomCalendarResponses } from '../src/services/roomCalendarRanges.ts'
import type { RoomCalendar } from '../src/services/roomCalendarService.ts'

test('calendar plan accepts a single inclusive day', () => {
  const plan = createRoomCalendarPlan('2026-10-15', '2026-10-15')
  assert.equal(plan.totalDays, 1)
  assert.deepEqual(plan.segments.map(({ startDate, days }) => ({ startDate, days })), [{ startDate: '2026-10-15', days: 1 }])
  assert.equal(plan.displayEndDate, '2026-10-15')
})

test('calendar plan accepts exactly 14 inclusive days and rejects 15', () => {
  const plan = createRoomCalendarPlan('2026-10-15', '2026-10-28')
  assert.equal(plan.totalDays, 14)
  assert.equal(plan.segments.length, 1)
  assert.equal(plan.segments[0].days, 14)
  assert.throws(() => createRoomCalendarPlan('2026-10-15', '2026-10-29'), /14/)
})

test('calendar plan rejects an end date before the start date', () => {
  assert.throws(() => createRoomCalendarPlan('2026-10-16', '2026-10-15'), /on or after/)
})

test('merged calendar contains only requested dates without UTC day shifts', () => {
  const plan = createRoomCalendarPlan('2026-03-07', '2026-03-08')
  const response: RoomCalendar = {
    startDate: '2026-03-07', endDateExclusive: '2026-03-09', dates: ['2026-03-07', '2026-03-08'],
    rooms: [{ roomId: 1, roomNumber: '101', roomType: 'Standard', cells: [
      { date: '2026-03-07', status: 'AVAILABLE', guestName: null, bookingCode: null, bookingId: null },
      { date: '2026-03-08', status: 'MAINTENANCE', guestName: null, bookingCode: null, bookingId: null },
    ] }],
  }
  const result = mergeRoomCalendarResponses([response], plan)
  assert.deepEqual(result.dates, ['2026-03-07', '2026-03-08'])
  assert.deepEqual(result.rooms[0].cells.map(cell => cell.status), ['AVAILABLE', 'MAINTENANCE'])
})
