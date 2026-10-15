import type { ApiAccess, EndpointPolicy, Role } from '../types/endpointPermission'

const INTERNAL_ROLES: readonly Role[] = [
  'ADMIN',
  'OWNER',
  'RECEPTIONIST',
  'HOUSEKEEPING',
]
const ADMIN_OWNER_RECEPTION: readonly Role[] = ['ADMIN', 'OWNER', 'RECEPTIONIST']

function policy(
  method: string | null,
  path: string,
  access: ApiAccess,
  roles: readonly Role[] = [],
): EndpointPolicy {
  return { method, path, access, roles }
}

function policies(
  method: string | null,
  access: ApiAccess,
  roles: readonly Role[],
  ...paths: string[]
): EndpointPolicy[] {
  return paths.map((path) => policy(method, path, access, roles))
}

export const APPROVED_ENDPOINT_MATRIX: readonly EndpointPolicy[] = [
  ...policies(null, 'PUBLIC', [], '/api/auth/login', '/api/auth/refresh', '/api/auth/logout',
    '/api/auth/forgot-password', '/api/auth/reset-password'),
  policy(null, '/api/internal/**', 'AUTHENTICATED', INTERNAL_ROLES),
  policy('GET', '/api/maintenance-requests/cleaning-room-ids', 'ROLES', ['HOUSEKEEPING']),
  ...policies('GET', 'ROLES', ['OWNER', 'RECEPTIONIST', 'HOUSEKEEPING'],
    '/api/maintenance-requests', '/api/maintenance-requests/*'),
  ...policies('POST', 'ROLES', ['OWNER'],
    '/api/maintenance-requests/*/accept', '/api/maintenance-requests/*/start',
    '/api/maintenance-requests/*/repair-completed', '/api/maintenance-requests/*/verify'),
  policy('POST', '/api/maintenance-requests/*/cleaning-completed', 'ROLES', ['HOUSEKEEPING']),
  policy('PUT', '/api/maintenance-requests/*/vendor', 'ROLES', ['OWNER']),
  ...policies('GET', 'ROLES', ['OWNER', 'RECEPTIONIST'],
    '/api/notifications', '/api/notifications/stream', '/api/notifications/unread-count'),
  ...policies('GET', 'ROLES', ['OWNER'],
    '/api/reports/occupancy', '/api/reports/occupancy/detailed', '/api/reports/occupancy.csv'),
  policy('PATCH', '/api/notifications/*/read', 'ROLES', ['OWNER', 'RECEPTIONIST']),
  policy(null, '/api/admin/audit-logs/**', 'ROLES', ['ADMIN']),
  policy('GET', '/api/admin/users', 'ROLES', ['ADMIN', 'OWNER']),
  policy('GET', '/api/admin/security/endpoint-permissions', 'ROLES', ['ADMIN']),
  policy('GET', '/api/admin/security/endpoint-discrepancies', 'ROLES', ['ADMIN']),
  policy('POST', '/api/admin/security/endpoint-discrepancies', 'ROLES', ['ADMIN']),
  policy(null, '/api/admin/**', 'ROLES', ['ADMIN']),
  policy(null, '/api/account/**', 'AUTHENTICATED', INTERNAL_ROLES),
  ...policies('GET', 'ROLES', ['RECEPTIONIST'],
    '/api/rooms/calendar', '/api/rooms/*/checkout-preview'),
  ...policies('GET', 'ROLES', INTERNAL_ROLES, '/api/rooms', '/api/rooms/**'),
  policy('PATCH', '/api/rooms/*/status', 'ROLES', ['RECEPTIONIST', 'HOUSEKEEPING']),
  policy('PATCH', '/api/rooms/*/maintenance', 'ROLES', ['RECEPTIONIST']),
  policy('PATCH', '/api/rooms/*/incident', 'ROLES', ['HOUSEKEEPING']),
  ...policies('POST', 'ROLES', ['RECEPTIONIST'],
    '/api/rooms/*/check-in', '/api/rooms/*/check-out'),
  policy(null, '/api/room-management/**', 'ROLES', ['ADMIN', 'OWNER']),
  ...policies('GET', 'ROLES', INTERNAL_ROLES, '/api/room-types', '/api/room-types/**'),
  policy(null, '/api/room-types/**', 'ROLES', ['ADMIN', 'OWNER']),
  ...policies('GET', 'ROLES', INTERNAL_ROLES, '/api/amenities', '/api/amenities/**'),
  policy(null, '/api/amenities/**', 'ROLES', ['ADMIN', 'OWNER']),
  policy('GET', '/api/settings/history', 'ROLES', ['OWNER', 'ADMIN']),
  policy('GET', '/api/settings', 'ROLES', ['OWNER', 'ADMIN', 'RECEPTIONIST']),
  policy('PUT', '/api/settings', 'ROLES', ['OWNER']),
  ...policies('GET', 'ROLES', ['OWNER', 'ADMIN', 'RECEPTIONIST'],
    '/api/price-overrides', '/api/price-overrides/**'),
  policy(null, '/api/price-overrides/**', 'ROLES', ['OWNER']),
  ...policies('POST', 'ROLES', ['RECEPTIONIST'],
    '/api/bookings', '/api/bookings/*/preview', '/api/bookings/*/cancel',
    '/api/bookings/*/no-show', '/api/appointments/*/no-show'),
  ...policies('POST', 'ROLES', ['OWNER', 'RECEPTIONIST'],
    '/api/bookings/*/deposit-adjustments', '/api/bookings/*/surcharges'),
  ...policies('PUT', 'ROLES', ['RECEPTIONIST'],
    '/api/bookings/*/confirm', '/api/bookings/*/room', '/api/bookings/*/room-change',
    '/api/bookings/*/cancel', '/api/bookings/*'),
  ...policies('GET', 'ROLES', ADMIN_OWNER_RECEPTION,
    '/api/bookings/*/room-change-history', '/api/bookings/*/available-rooms'),
  ...policies('PUT', 'ROLES', ['OWNER', 'RECEPTIONIST'],
    '/api/bookings/*/deposit', '/api/bookings/*/deposits/**'),
  ...policies('DELETE', 'ROLES', ['OWNER', 'RECEPTIONIST'],
    '/api/bookings/*/deposit', '/api/bookings/*/deposits/**', '/api/bookings/*'),
  policy('GET', '/api/pricing/**', 'ROLES', ADMIN_OWNER_RECEPTION),
  ...policies('GET', 'ROLES', ADMIN_OWNER_RECEPTION, '/api/bookings', '/api/bookings/**', '/api/appointments/**'),
  ...policies('GET', 'PUBLIC', [],
    '/api/public/room-types/**',
    '/api/public/room-types-catalog', '/api/public/quote',
    '/api/public/room-type-cards', '/api/public/rooms/**'),
  ...policies('POST', 'PUBLIC', [], '/api/public/bookings', '/api/public/bookings/lookup'),
  policy('POST', '/api/public/bookings/walk-in', 'ROLES', ['RECEPTIONIST']),
  policy(null, '/api', 'DENIED'),
  policy(null, '/api/**', 'DENIED'),
]
