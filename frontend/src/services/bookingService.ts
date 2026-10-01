import { apiRequest } from './apiClient'
import type { BookingListItem } from '../types/booking'

export function getLatestBookings() {
  return apiRequest<BookingListItem[]>('/api/bookings')
}