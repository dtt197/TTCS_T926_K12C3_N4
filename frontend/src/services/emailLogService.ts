import { apiRequest } from './apiClient'

export type EmailLogItem = {
  id: number
  bookingId: number
  bookingCode: string | null
  emailType: string
  recipientEmail: string | null
  status: 'SENT' | 'FAILED'
  errorMessage: string | null
  sentAt: string
}

export type ReminderRunResult = {
  selected: number
  sent: number
  failed: number
}

export function getEmailLogs(type = 'REMINDER'): Promise<EmailLogItem[]> {
  return apiRequest<EmailLogItem[]>(`/api/admin/email-logs?type=${type}`)
}

/** date = ngày "hôm nay" giả lập (yyyy-MM-dd); nhắc cho booking nhận phòng vào ngày kế tiếp. */
export function runReminders(date?: string): Promise<ReminderRunResult> {
  const query = date ? `?date=${encodeURIComponent(date)}` : ''
  return apiRequest<ReminderRunResult>(`/api/admin/reminders/run${query}`, {
    method: 'POST',
  })
}