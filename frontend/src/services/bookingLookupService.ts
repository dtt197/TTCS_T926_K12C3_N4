import { apiRequest } from './apiClient'
import type { BookingLookupResult } from '../types/bookingLookup'

/** S2-08: khách tra cứu booking bằng mã booking và email, không cần đăng nhập. */
export function lookupBooking(bookingCode: string, email: string) {
  return apiRequest<BookingLookupResult>('/api/public/bookings/lookup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ bookingCode, email }),
  })
}