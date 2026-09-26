import { apiRequest } from './apiClient'
import type { CreateUserPayload, StaffUser } from '../types/user'

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