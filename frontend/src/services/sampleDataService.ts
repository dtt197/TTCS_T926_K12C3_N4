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
  const res = await apiRequest('/api/admin/sample-data/reload', {
    method: 'POST',
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`Nạp dữ liệu thất bại (${res.status}): ${text}`)
  }
  return res.json() as Promise<SampleDataSummary>
}
