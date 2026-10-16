import { apiRequest } from './apiClient'

export interface SampleDataSummary {
  roomTypeCount: number
  roomCount: number
  priceOverrideCount: number
  bookingCount: number
}

/**
 * S4-09: Nạp lại dữ liệu mẫu về trạng thái ban đầu.
 */
export async function reloadSampleData(): Promise<SampleDataSummary> {
  return apiRequest<SampleDataSummary>('/api/admin/sample-data/reload', {
    method: 'POST',
  })
}
