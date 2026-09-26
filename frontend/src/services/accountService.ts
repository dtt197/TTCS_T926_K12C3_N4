import { apiRequest } from './apiClient'

export type ChangePasswordPayload = {
  currentPassword: string
  newPassword: string
}

/** S1-02 Lát 2 / S1-03: người dùng tự đổi mật khẩu của mình. */
export function changePassword(payload: ChangePasswordPayload) {
  return apiRequest<void>('/api/account/change-password', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}
