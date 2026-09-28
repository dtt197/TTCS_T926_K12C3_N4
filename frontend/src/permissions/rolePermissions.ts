
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
],
OWNER: [
  'accounts:view',
  'rooms:view',
  'rooms:manage',
],
RECEPTIONIST: [
  'rooms:view',
  'rooms:status:update',
  'rooms:maintenance',
  'rooms:check-in',
  'rooms:check-out',
],
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
