import { apiRequest } from './apiClient'

export type CalendarStatus = 'AVAILABLE' | 'BOOKED' | 'MAINTENANCE'
export type RoomCalendar = {
  startDate: string
  endDateExclusive: string
  dates: string[]
  rooms: {
    roomId: number
    roomNumber: string
    roomType: string
    cells: { date: string; status: CalendarStatus; guestName: string | null; bookingCode: string | null; bookingId: number | null }[]
  }[]
}

export function getRoomCalendar(startDate: string, signal: AbortSignal) {
  const params = new URLSearchParams({ startDate, days: '14' })
  return apiRequest<RoomCalendar>(`/api/rooms/calendar?${params}`, { signal })
}
