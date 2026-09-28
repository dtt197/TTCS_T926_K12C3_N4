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
  | 'rooms:check-in'
  | 'rooms:check-out'
  | 'roomTypes:view'
  | 'roomTypes:manage'
  | 'auditLogs:view'

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
  ],
  OWNER: [
    'accounts:view',
    'rooms:view',
    'rooms:manage',
    'roomTypes:view',
    'roomTypes:manage',
  ],
  RECEPTIONIST: [
    'rooms:view',
    'rooms:status:update',
    'rooms:maintenance',
    'rooms:check-in',
    'rooms:check-out',
    'roomTypes:view',
  ],
  // S1-04 AC1: Buồng phòng chỉ thấy danh sách phòng cần dọn, không có menu Loại phòng.
  HOUSEKEEPING: [
    'rooms:view',
    'rooms:clean',
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