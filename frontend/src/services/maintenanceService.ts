import { apiRequest } from './apiClient'

export type MaintenanceRequest = {
  id: number; roomId: number; roomNumber: string; severity: string; description: string
  status: string; reportedById: number; reportedByName: string; reportedAt: string
  acceptedById: number | null; acceptedByName: string | null; acceptedAt: string | null
  vendorName: string | null; vendorPhone: string | null; vendorNotes: string | null
  processingStartedAt: string | null
  repairCompletedById: number | null; repairCompletedByName: string | null
  repairCompletedAt: string | null; repairNotes: string | null
  verifiedById: number | null; verifiedByName: string | null
  verifiedAt: string | null; verificationNotes: string | null
  cleanedById: number | null; cleanedByName: string | null; cleanedAt: string | null
  cleaningNotes: string | null; completedAt: string | null
}
export type MaintenanceHistory = {
  id: number; action: string; previousStatus: string; newStatus: string
  actorName: string; occurredAt: string; vendorName: string | null
  vendorPhone: string | null; notes: string | null
}
export type MaintenanceDetail = { request: MaintenanceRequest; history: MaintenanceHistory[] }
export type MaintenancePage = { content: MaintenanceRequest[]; page: number; totalPages: number; totalElements: number }
export type VendorInput = { name: string; phone: string; notes: string }

export const getMaintenanceRequests = (page: number, signal?: AbortSignal) =>
  apiRequest<MaintenancePage>(`/api/maintenance-requests?page=${page}&size=20`, { signal })
export const getMaintenanceCleaningRoomIds = (signal?: AbortSignal) =>
  apiRequest<number[]>('/api/maintenance-requests/cleaning-room-ids', { signal })
export const getMaintenanceDetail = (id: number, signal?: AbortSignal) =>
  apiRequest<MaintenanceDetail>(`/api/maintenance-requests/${id}`, { signal })
export const updateMaintenance = (id: number, action: 'accept' | 'vendor' | 'start', vendor?: VendorInput, signal?: AbortSignal) =>
  apiRequest<MaintenanceRequest>(`/api/maintenance-requests/${id}/${action}`, {
    method: action === 'vendor' ? 'PUT' : 'POST', signal,
    ...(vendor ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(vendor) } : {}),
  })
export const completeRepair = (id: number, notes: string, signal?: AbortSignal) =>
  apiRequest<MaintenanceRequest>(`/api/maintenance-requests/${id}/repair-completed`, {
    method: 'POST', signal, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ notes }),
  })
export const verifyRepair = (id: number, passed: boolean, notes: string, signal?: AbortSignal) =>
  apiRequest<MaintenanceRequest>(`/api/maintenance-requests/${id}/verify?passed=${passed}`, {
    method: 'POST', signal, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ notes }),
  })
export const completeCleaning = (id: number, notes: string, signal?: AbortSignal) =>
  apiRequest<MaintenanceRequest>(`/api/maintenance-requests/${id}/cleaning-completed`, {
    method: 'POST', signal, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ notes }),
  })
