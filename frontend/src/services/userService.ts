import { apiRequest } from './apiClient'
import type { CreateUserPayload, StaffUser, UpdateUserPayload } from '../types/user'

/** S1-02: gọi API quản lý tài khoản (chỉ ADMIN). apiRequest tự gắn token đăng nhập. */
export function getUsers() {
  return apiRequest<StaffUser[]>('/api/admin/users')
}

export function createUser(payload: CreateUserPayload) {
  return apiRequest<StaffUser>('/api/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...payload, phone: payload.phone.trim() || null }),
  })
}

export function updateUserStatus(id: number, active: boolean) {
  return apiRequest<StaffUser>(`/api/admin/users/${id}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ active }),
  })
}

export function updateUser(id: number, payload: UpdateUserPayload) {
  return apiRequest<StaffUser>(`/api/admin/users/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...payload, phone: payload.phone.trim() || null }),
  })
}

export function resendTemporaryPassword(id: number) {
  return apiRequest<StaffUser>(`/api/admin/users/${id}/resend-temporary-password`, { method: 'POST' })
}