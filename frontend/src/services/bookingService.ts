import { apiRequest } from './apiClient'
import type { BookingListItem, PageResponse } from '../types/booking'

/** S2-10: booking mới nhất trước, 20 dòng mỗi trang (trang đầu tiên là 0). */
export function getLatestBookings(page = 0) {
  return apiRequest<PageResponse<BookingListItem>>(`/api/bookings?page=${page}`)
}