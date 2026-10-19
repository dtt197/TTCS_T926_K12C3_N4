export type Role =
  | 'ADMIN'
  | 'OWNER'
  | 'RECEPTIONIST'
  | 'HOUSEKEEPING'

export type Permission =
  | 'rooms:calendar'
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
  | 'bookings:deposit-adjust'
  | 'bookings:cancel'
  | 'bookings:manage'
  | 'reports:revenue'
  | 'bookings:surcharge-add'
  | 'reports:occupancy'
  | 'security:matrix:view'
  | 'sampleData:manage'
  | 'guests:identity:view'

/** S1-04: ma trận quyền tập trung. S1-06: loại phòng (Chủ homestay, Admin = F; Lễ tân = R). */
export const ROLE_PERMISSIONS: Record<Role, readonly Permission[]> = {
  ADMIN: [
    'accounts:view',
    'accounts:manage',
    'rooms:view',
    'rooms:manage',
    'roomTypes:view',
    'roomTypes:manage',
    'auditLogs:view',
    'security:matrix:view',
    'amenities:view',
    'amenities:manage',
    'settings:view',
    'settings:history',
    'pricing:view',
    'bookings:view',
    'reports:revenue',
    // S4-06: Quản trị được xem báo cáo công suất (R)
    'reports:occupancy',
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
    'bookings:deposit-adjust',
    'reports:revenue',
    'bookings:surcharge-add',
    'reports:occupancy',
    'sampleData:manage',
    // Xem đầy đủ số giấy tờ khách (có ghi nhật ký)
    'guests:identity:view',
  ],
  RECEPTIONIST: [
    'rooms:calendar',
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
    'bookings:deposit-adjust',
    'bookings:surcharge-add',
    'bookings:cancel',
    'bookings:manage',
    'reports:revenue',
    // S4-06: Lễ tân được xem báo cáo công suất (R)
    'reports:occupancy',
    // Xem đầy đủ số giấy tờ khách (có ghi nhật ký)
    'guests:identity:view',
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
