import { apiRequest } from './apiClient'
import type {
  BookingCancellationResult,
  CancelBookingInput,
  CancellationPreview,
} from '../types/booking'

/** S3-05: xem trước tỷ lệ và số tiền hoàn cọc, chưa huỷ. */
export function getCancellationPreview(
  bookingCode: string,
): Promise<CancellationPreview> {
  return apiRequest<CancellationPreview>(
    `/api/bookings/${encodeURIComponent(bookingCode)}/cancellation-preview`,
  )
}

/** S3-05: xác nhận huỷ. Số tiền hoàn do hệ thống tính, không gửi từ giao diện. */
export function cancelBooking(
  bookingCode: string,
  input: CancelBookingInput,
): Promise<BookingCancellationResult> {
  return apiRequest<BookingCancellationResult>(
    `/api/bookings/${encodeURIComponent(bookingCode)}/cancel`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
  )
}