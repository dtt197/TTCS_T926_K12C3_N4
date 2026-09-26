export type StaffRole = 'RECEPTIONIST' | 'HOUSEKEEPING' | 'OWNER' | 'ADMIN'

/** AC2: bốn vai trò nội bộ, hiển thị tên tiếng Việt. */
export const ROLE_LABELS: Record<StaffRole, string> = {
  RECEPTIONIST: 'Lễ tân',
  HOUSEKEEPING: 'Buồng phòng',
  OWNER: 'Chủ homestay',
  ADMIN: 'Quản trị hệ thống',
}

export type StaffUser = {
  id: number
  fullName: string
  email: string
  phone: string | null
  role: StaffRole
  active: boolean
  mustChangePassword: boolean
  createdAt: string
}

export type CreateUserPayload = {
  fullName: string
  email: string
  phone: string
  role: StaffRole
  active: boolean
}