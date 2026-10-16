import { apiRequest } from './apiClient'

export type ReminderRun = {
  id: number
  startedAt: string
  finishedAt: string | null
  processingDate: string
  checkInDate: string
  triggerType: 'MANUAL' | 'SCHEDULED'
  status: 'RUNNING' | 'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'FAILED'
  selectedCount: number
  sentCount: number
  skippedCount: number
  failedCount: number
  errorMessage: string | null
}

export type RunEmailItem = {
  id: number
  bookingId: number
  recipientEmail: string | null
  status: string
  sentAt: string
}

export type RunPage<T> = {
  content: T[]
  number: number
  totalPages: number
  totalElements: number
  first: boolean
  last: boolean
}

export function getReminderRuns(page = 0) {
  return apiRequest<RunPage<ReminderRun>>(
    `/api/admin/reminder-runs?page=${page}&size=10`,
  )
}

export function getReminderRunEmails(runId: number, page = 0) {
  return apiRequest<RunPage<RunEmailItem>>(
    `/api/admin/reminder-runs/${runId}/emails?page=${page}&size=10`,
  )
}