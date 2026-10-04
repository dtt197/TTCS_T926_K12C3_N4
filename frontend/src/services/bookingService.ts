import { apiRequest } from './apiClient'
import type {
  BookingListItem,
  BookingStatus,
  PageResponse,
} from '../types/booking'
<<<<<<< HEAD

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
=======

export type BookingPage = PageResponse<BookingListItem>

export type BookingFilters = {
  status: BookingStatus | ''
  checkInFrom: string
  checkInTo: string
  keyword: string
}

export async function searchBookings(filters: {
  keyword?: string
  status?: BookingStatus | ''
  checkInFrom?: string
  checkInTo?: string
  page?: number
  size?: number
}): Promise<PageResponse<BookingListItem>> {
  const params = new URLSearchParams()

  if (filters.keyword?.trim()) {
    params.set('keyword', filters.keyword.trim())
  }

  if (filters.status) {
    params.set('status', filters.status)
  }

  if (filters.checkInFrom) {
    params.set('checkInFrom', filters.checkInFrom)
  }

  if (filters.checkInTo) {
    params.set('checkInTo', filters.checkInTo)
  }

  params.set('page', String(filters.page ?? 0))
  if (filters.size !== undefined) {
    params.set('size', String(filters.size))
  }

  return apiRequest<PageResponse<BookingListItem>>(
    `/api/bookings?${params.toString()}`,
  )
}

/** S2-10: booking mới nhất trước, 20 dòng mỗi trang (trang đầu tiên là 0). */
export function getLatestBookings(
  page = 0,
  filters?: Partial<BookingFilters>,
): Promise<PageResponse<BookingListItem>> {
  return searchBookings({
    page,
    status: filters?.status,
    checkInFrom: filters?.checkInFrom,
    checkInTo: filters?.checkInTo,
    keyword: filters?.keyword,
  })
>>>>>>> 6c8117e (Fix bugs and complete booking feature)
}