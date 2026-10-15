import { apiRequest } from './apiClient'
import type { BookingSurchargeList, BookingSurchargeRequest } from '../types/bookingSurcharge'

export function getBookingSurcharges(bookingId: number): Promise<BookingSurchargeList> {
  return apiRequest<BookingSurchargeList>(`/api/bookings/${bookingId}/surcharges`)
}

export function addBookingSurcharge(
  bookingId: number,
  request: BookingSurchargeRequest,
): Promise<BookingSurchargeList> {
  return apiRequest<BookingSurchargeList>(`/api/bookings/${bookingId}/surcharges`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}