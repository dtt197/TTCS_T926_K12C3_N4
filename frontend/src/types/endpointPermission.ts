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

export type EndpointDiscrepancyTicket = {
  id: number
  method: string
  path: string
  approvedAccess: ApiAccess
  approvedRoles: Role[]
  actualAccess: ApiAccess
  actualRoles: Role[]
  description: string
  assigneeId: number
  assigneeName: string
  assigneeRole: Role
  createdByName: string
  createdAt: string
}

export type CreateEndpointDiscrepancyPayload = {
  method: string
  path: string
  approvedAccess: ApiAccess
  approvedRoles: readonly Role[]
  assigneeId: number
  description: string
}
