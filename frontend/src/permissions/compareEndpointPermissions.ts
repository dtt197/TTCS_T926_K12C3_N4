import { APPROVED_ENDPOINT_MATRIX } from './approvedEndpointMatrix.ts'
import type {
  ApiAccess,
  EndpointPermission,
  EndpointPermissionComparison,
  EndpointPolicy,
  Role,
} from '../types/endpointPermission'

function toPathRegex(pattern: string): RegExp {
  const segments = pattern.split('/')
  let expression = '^'

  for (const segment of segments.slice(1)) {
    if (segment === '**') {
      expression += '(?:/.*)?'
    } else {
      expression += '/'
      expression += segment === '*' || /^\{[^/]+\}$/.test(segment)
        ? '[^/]+'
        : segment.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    }
  }

  return new RegExp(`${expression}$`)
}

function findApprovedPolicy(
  endpoint: EndpointPermission,
  approvedPolicies: readonly EndpointPolicy[],
): EndpointPolicy {
  return approvedPolicies.find((policy) =>
    (policy.method === null || policy.method === endpoint.method)
    && toPathRegex(policy.path).test(endpoint.path),
  ) ?? {
    method: endpoint.method,
    path: endpoint.path,
    access: 'DENIED',
    roles: [],
  }
}

function sortedRoles(roles: readonly string[]): string[] {
  return [...roles].sort()
}

export function compareEndpointPermissions(
  endpoints: readonly EndpointPermission[],
  approvedPolicies: readonly EndpointPolicy[] = APPROVED_ENDPOINT_MATRIX,
): EndpointPermissionComparison[] {
  return endpoints.map((endpoint) => {
    const approved = findApprovedPolicy(endpoint, approvedPolicies)
    const hasMismatch = endpoint.access !== approved.access
      || JSON.stringify(sortedRoles(endpoint.roles))
        !== JSON.stringify(sortedRoles(approved.roles))

    return { endpoint, approved, hasMismatch }
  })
}

export function filterEndpointPermissionComparisons(
  comparisons: readonly EndpointPermissionComparison[],
  filters: {
    mismatchesOnly: boolean
    role: Role | 'ALL'
    query: string
  },
): EndpointPermissionComparison[] {
  const normalizedQuery = filters.query.trim().toLowerCase()

  return comparisons.filter((item) => {
    if (filters.mismatchesOnly && !item.hasMismatch) return false
    if (filters.role !== 'ALL'
      && !item.endpoint.roles.includes(filters.role)
      && !item.approved.roles.includes(filters.role)) return false

    return !normalizedQuery
      || `${item.endpoint.method} ${item.endpoint.path}`
        .toLowerCase()
        .includes(normalizedQuery)
  })
}

export function accessLabel(access: ApiAccess): string {
  switch (access) {
    case 'PUBLIC':
      return 'Công khai'
    case 'AUTHENTICATED':
      return 'Cần đăng nhập'
    case 'ROLES':
      return 'Theo vai trò'
    case 'DENIED':
      return 'Bị từ chối'
  }
}
