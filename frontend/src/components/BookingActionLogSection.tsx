import { useEffect, useState } from 'react'
import { getAppointmentActionLogs } from '../services/bookingService'
import type { AppointmentActionLog } from '../types/booking'
import './BookingActionLogSection.css'

type BookingActionLogSectionProps = {
  bookingId: number
}

const ACTION_LABELS: Record<string, string> = {
  MARK_NO_SHOW: 'Đánh dấu khách không đến',
  NO_SHOW: 'Đánh dấu khách không đến',
  UNDO_NO_SHOW: 'Hoàn tác đánh dấu không đến',
}

const ACTION_CLASSES: Record<string, string> = {
  MARK_NO_SHOW: 'action-no-show',
  NO_SHOW: 'action-no-show',
  UNDO_NO_SHOW: 'action-undo-no-show',
}

export function BookingActionLogSection({ bookingId }: BookingActionLogSectionProps) {
  const [logs, setLogs] = useState<AppointmentActionLog[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let isMounted = true
    getAppointmentActionLogs(bookingId)
      .then((data) => {
        if (isMounted) {
          setLogs(data)
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Không tải được nhật ký thao tác.')
        }
      })
      .finally(() => {
        if (isMounted) {
          setLoading(false)
        }
      })

    return () => {
      isMounted = false
    }
  }, [bookingId])

  if (loading) {
    return <p className="action-logs-loading">Đang tải nhật ký thao tác...</p>
  }

  if (error) {
    return <div className="alert alert-error">{error}</div>
  }

  if (logs.length === 0) {
    return null
  }

  return (
    <section className="booking-detail-section booking-detail-full" aria-labelledby="action-logs-title">
      <h4 id="action-logs-title">Nhật ký thao tác Lễ tân (No-show & Hoàn tác)</h4>
      <div className="action-logs-table-wrapper">
        <table className="action-logs-table" data-testid="appointment-action-logs-table">
          <thead>
            <tr>
              <th scope="col">Thời gian</th>
              <th scope="col">Thao tác</th>
              <th scope="col">Người thực hiện</th>
              <th scope="col">Trạng thái trước</th>
            </tr>
          </thead>
          <tbody>
            {logs.map((log) => (
              <tr key={log.id} data-testid={`action-log-row-${log.id}`}>
                <td>{new Date(log.createdAt).toLocaleString('vi-VN')}</td>
                <td>
                  <span className={`action-badge ${ACTION_CLASSES[log.actionType] ?? ''}`}>
                    {ACTION_LABELS[log.actionType] ?? log.actionType}
                  </span>
                </td>
                <td>
                  <strong>{log.receptionistName || 'Lễ tân'}</strong>
                  {log.receptionistId != null && (
                    <span className="receptionist-id-tag"> (ID: {log.receptionistId})</span>
                  )}
                </td>
                <td>{log.previousStatus || '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
