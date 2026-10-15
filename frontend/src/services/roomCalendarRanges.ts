import type { RoomCalendar } from './roomCalendarService'

export const ROOM_CALENDAR_REQUEST_MAX_DAYS = 14
const MILLISECONDS_PER_DAY = 86_400_000

export type RoomCalendarRange = {
  startDate: string
  days: number
  endDateExclusive: string
}

export type RoomCalendarPlan = {
  startDate: string
  selectedEndDate: string
  displayEndDate: string
  endDateExclusive: string
  totalDays: number
  segments: RoomCalendarRange[]
}

function parseCalendarDate(value: string, fieldName: string): number {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) throw new RangeError(`${fieldName} must use YYYY-MM-DD`)

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(0)
  date.setUTCHours(0, 0, 0, 0)
  date.setUTCFullYear(year, month - 1, day)
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    throw new RangeError(`${fieldName} is not a valid calendar date`)
  }
  return date.getTime() / MILLISECONDS_PER_DAY
}

function formatUtcDay(dayNumber: number): string {
  const date = new Date(dayNumber * MILLISECONDS_PER_DAY)
  const year = date.getUTCFullYear()
  if (year < 1 || year > 9999) throw new RangeError('Calendar range exceeds supported YYYY-MM-DD dates')
  return `${String(year).padStart(4, '0')}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`
}

function datesForRange(startDate: string, days: number): string[] {
  const startDay = parseCalendarDate(startDate, 'startDate')
  return Array.from({ length: days }, (_, index) => formatUtcDay(startDay + index))
}

/** Builds an inclusive selected range limited to one API request (1–14 days). */
export function createRoomCalendarPlan(startDate: string, selectedEndDate: string): RoomCalendarPlan {
  const startDay = parseCalendarDate(startDate, 'startDate')
  const selectedEndDay = parseCalendarDate(selectedEndDate, 'selectedEndDate')
  if (selectedEndDay < startDay) throw new RangeError('selectedEndDate must be on or after startDate')

  const totalDays = selectedEndDay - startDay + 1
  if (totalDays > ROOM_CALENDAR_REQUEST_MAX_DAYS) {
    throw new RangeError('Khoảng ngày xem không được vượt quá 14 ngày.')
  }
  const displayEndDate = selectedEndDate
  const endDateExclusive = formatUtcDay(startDay + totalDays)
  const segments: RoomCalendarRange[] = []
  for (let offset = 0; offset < totalDays; offset += ROOM_CALENDAR_REQUEST_MAX_DAYS) {
    const segmentStartDay = startDay + offset
    const days = Math.min(ROOM_CALENDAR_REQUEST_MAX_DAYS, totalDays - offset)
    segments.push({
      startDate: formatUtcDay(segmentStartDay),
      days,
      endDateExclusive: formatUtcDay(segmentStartDay + days),
    })
  }

  return { startDate, selectedEndDate, displayEndDate, endDateExclusive, totalDays, segments }
}

function assertExactDates(actual: string[], expected: string[], context: string) {
  if (actual.length !== expected.length || actual.some((date, index) => date !== expected[index])) {
    throw new Error(`${context} dates do not match the requested contiguous range`)
  }
}

/** Validates and combines every planned segment; response order is irrelevant. */
export function mergeRoomCalendarResponses(
  responses: readonly RoomCalendar[],
  plan: RoomCalendarPlan,
): RoomCalendar {
  if (responses.length !== plan.segments.length) {
    throw new Error(`Expected ${plan.segments.length} calendar responses, received ${responses.length}`)
  }

  const responseByStartDate = new Map<string, RoomCalendar>()
  for (const response of responses) {
    if (responseByStartDate.has(response.startDate)) {
      throw new Error(`Duplicate calendar response for ${response.startDate}`)
    }
    responseByStartDate.set(response.startDate, response)
  }

  const orderedResponses = plan.segments.map(segment => {
    const response = responseByStartDate.get(segment.startDate)
    if (!response) throw new Error(`Missing calendar response for ${segment.startDate}`)
    if (response.endDateExclusive !== segment.endDateExclusive) {
      throw new Error(`Calendar response endDateExclusive does not match segment ${segment.startDate}`)
    }
    assertExactDates(response.dates, datesForRange(segment.startDate, segment.days), `Segment ${segment.startDate}`)
    return { segment, response }
  })

  const firstRooms = orderedResponses[0]?.response.rooms
  if (!firstRooms) throw new Error('Calendar plan must contain at least one segment')
  const roomIds = new Set<number>()
  for (const room of firstRooms) {
    if (roomIds.has(room.roomId)) throw new Error(`Duplicate roomId ${room.roomId} in calendar response`)
    roomIds.add(room.roomId)
  }

  for (const { response, segment } of orderedResponses) {
    const segmentRoomIds = new Set<number>()
    for (const room of response.rooms) {
      if (segmentRoomIds.has(room.roomId)) throw new Error(`Duplicate roomId ${room.roomId} in segment ${segment.startDate}`)
      segmentRoomIds.add(room.roomId)
    }
    if (segmentRoomIds.size !== roomIds.size || [...roomIds].some(roomId => !segmentRoomIds.has(roomId))) {
      throw new Error(`Room set differs in calendar segment ${segment.startDate}`)
    }
  }

  const allDates = datesForRange(plan.startDate, plan.totalDays)
  const roomsById = new Map<number, RoomCalendar['rooms'][number]>()
  for (const room of firstRooms) {
    roomsById.set(room.roomId, { ...room, cells: [] })
  }

  for (const { response, segment } of orderedResponses) {
    const dateSet = new Set(response.dates)
    for (const sourceRoom of response.rooms) {
      const cellByDate = new Map<string, RoomCalendar['rooms'][number]['cells'][number]>()
      for (const cell of sourceRoom.cells) {
        if (!dateSet.has(cell.date)) throw new Error(`Unexpected cell date ${cell.date} in room ${sourceRoom.roomId}`)
        if (cellByDate.has(cell.date)) throw new Error(`Duplicate cell for room ${sourceRoom.roomId} on ${cell.date}`)
        cellByDate.set(cell.date, cell)
      }
      if (cellByDate.size !== segment.days || response.dates.some(date => !cellByDate.has(date))) {
        throw new Error(`Missing cell for room ${sourceRoom.roomId} in segment ${segment.startDate}`)
      }
      const target = roomsById.get(sourceRoom.roomId)
      if (!target) throw new Error(`Unexpected roomId ${sourceRoom.roomId}`)
      target.cells.push(...response.dates.map(date => cellByDate.get(date)!))
    }
  }

  for (const [roomId, room] of roomsById) {
    assertExactDates(room.cells.map(cell => cell.date), allDates, `Room ${roomId}`)
    if (room.cells.length !== plan.totalDays) throw new Error(`Room ${roomId} does not have exactly one cell per display date`)
  }

  return {
    startDate: plan.startDate,
    endDateExclusive: plan.endDateExclusive,
    dates: allDates,
    rooms: [...roomsById.values()],
  }
}
