import { apiRequest } from './apiClient'
import type { PriceOverride, PriceOverridePayload } from '../types/priceOverride'

/** S2-02: gọi API giá đè. apiRequest tự gắn token đăng nhập. */
const JSON_HEADERS = { 'Content-Type': 'application/json' }

export function getPriceOverrides() {
  return apiRequest<PriceOverride[]>('/api/price-overrides')
}

export function createPriceOverride(payload: PriceOverridePayload) {
  return apiRequest<PriceOverride>('/api/price-overrides', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(payload),
  })
}

export function updatePriceOverride(id: number, payload: PriceOverridePayload) {
  return apiRequest<PriceOverride>(`/api/price-overrides/${id}`, {
    method: 'PUT',
    headers: JSON_HEADERS,
    body: JSON.stringify(payload),
  })
}

export function deletePriceOverride(id: number) {
  return apiRequest<void>(`/api/price-overrides/${id}`, { method: 'DELETE' })
}