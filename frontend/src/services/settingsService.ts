import { apiRequest } from './apiClient'
import type { OperatingSettings, OperatingSettingsPayload } from '../types/settings'

/** S1-09: gọi API tham số vận hành. apiRequest tự gắn token đăng nhập. */
export function getSettings() {
  return apiRequest<OperatingSettings>('/api/settings')
}

export function getSettingsHistory() {
  return apiRequest<OperatingSettings[]>('/api/settings/history')
}

export function updateSettings(payload: OperatingSettingsPayload) {
  return apiRequest<OperatingSettings>('/api/settings', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}