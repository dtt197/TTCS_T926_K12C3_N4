export type Role =
  | 'ADMIN'
  | 'OWNER'
  | 'RECEPTIONIST'
  | 'HOUSEKEEPING'

export type Permission =
  | 'accounts:view'
  | 'accounts:manage'
  | 'rooms:view'
  | 'rooms:manage'
  | 'rooms:status:update'
  | 'rooms:clean'
  | 'rooms:maintenance'
  | 'rooms:report-incident'
  | 'rooms:check-in'
  | 'rooms:check-out'
  | 'roomTypes:view'
  | 'roomTypes:manage'
  | 'auditLogs:view'
  | 'amenities:view'
  | 'amenities:manage'
  | 'settings:view'
  | 'settings:history'
  | 'settings:manage'
  | 'pricing:view'
  | 'pricing:manage'
  | 'bookings:view'
/** S1-04: ma trận quyền tập trung. S1-06: loại phòng (Chủ homestay, Admin = F; Lễ tân = R). */
export const ROLE_PERMISSIONS: Record<Role, readonly Permission[]> = {
  ADMIN: [
    'accounts:view',
    'accounts:manage',
    'rooms:view',
    'rooms:manage',
    'rooms:status:update',
    'rooms:clean',
    'rooms:maintenance',
    'rooms:check-in',
    'rooms:check-out',
    'roomTypes:view',
    'roomTypes:manage',
    'auditLogs:view',
    'amenities:view',
    'amenities:manage',
    'settings:view',
    'settings:history',
    'pricing:view',
    'bookings:view',
  ],
  OWNER: [
    'accounts:view',
    'rooms:view',
    'rooms:manage',
    'roomTypes:view',
    'roomTypes:manage',
    'amenities:view',
    'amenities:manage',
    'settings:view',
    'settings:history',
    'settings:manage',
    'pricing:view',
    'pricing:manage',
    'bookings:view',
  ],
  RECEPTIONIST: [
    'rooms:view',
    'rooms:status:update',
    'rooms:maintenance',
    'rooms:check-in',
    'rooms:check-out',
    'roomTypes:view',
    'amenities:view',
    'settings:view',
    'pricing:view',
    'bookings:view',
  ],
  // S1-04 AC1: Buồng phòng chỉ thấy danh sách phòng cần dọn, không có menu Loại phòng.
  HOUSEKEEPING: [
    'rooms:view',
    'rooms:clean',
    'rooms:report-incident',
  ],
}

export function hasPermission(
  role: string,
  permission: Permission,
): boolean {
  if (!Object.prototype.hasOwnProperty.call(ROLE_PERMISSIONS, role)) {
    return false
  }

  return ROLE_PERMISSIONS[role as Role].includes(permission)
}