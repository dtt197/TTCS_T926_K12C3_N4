import { apiRequest } from './apiClient'
import type {
  BookingListItem,
  BookingStatus,
  PageResponse,
} from '../types/booking'

export type BookingFilters = {
  status: BookingStatus | ''
  checkInFrom: string
  checkInTo: string
  keyword: string
}

export function getLatestBookings(
  page = 0,
  filters?: Partial<BookingFilters>,
) {
  const params = new URLSearchParams({
    page: String(page),
  })

  if (filters?.status) {
    params.set('status', filters.status)
  }

  if (filters?.checkInFrom) {
    params.set('checkInFrom', filters.checkInFrom)
  }

  if (filters?.checkInTo) {
    params.set('checkInTo', filters.checkInTo)
  }

  if (filters?.keyword?.trim()) {
    params.set('keyword', filters.keyword.trim())
  }

  return apiRequest<PageResponse<BookingListItem>>(
    `/api/bookings?${params.toString()}`,
  )
}