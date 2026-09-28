import { apiRequest } from './apiClient'

export type AuditLogEntry = {
  id: number
  occurredAt: string
  actorEmail: string | null
  accountEmail: string | null
  action: string
  result: string
  ipAddress: string | null
}

export type AuditLogPage = {
  content: AuditLogEntry[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type AuditLogFilters = {
  from: string
  to: string
  account: string
  page: number
}

export function getAuditLogs(filters: AuditLogFilters) {
  const params = new URLSearchParams()
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.account.trim()) params.set('account', filters.account.trim())
  params.set('page', String(filters.page))
  return apiRequest<AuditLogPage>(`/api/admin/audit-logs?${params.toString()}`)
}