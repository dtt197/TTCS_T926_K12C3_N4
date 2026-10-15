export type Role = 'ADMIN' | 'OWNER' | 'RECEPTIONIST' | 'HOUSEKEEPING'

export type ApiAccess = 'PUBLIC' | 'AUTHENTICATED' | 'ROLES' | 'DENIED'

export type EndpointPolicy = {
  method: string | null
  path: string
  access: ApiAccess
  roles: readonly Role[]
}

export type EndpointPermission = {
  method: string
  path: string
  access: ApiAccess
  roles: Role[]
}

export type EndpointPermissionComparison = {
  endpoint: EndpointPermission
  approved: EndpointPolicy
  hasMismatch: boolean
}
