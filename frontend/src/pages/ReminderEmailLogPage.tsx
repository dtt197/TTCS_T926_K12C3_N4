import { useEffect, useState } from 'react'
import {
  getEmailLogs,
  runReminders,
  type EmailLogItem,
  type ReminderRunResult,
} from '../services/emailLogService'
import './AuditLogPage.css'

function todayInVietnam() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date())
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(value))
}

export function ReminderEmailLogPage() {
  const [logs, setLogs] = useState<EmailLogItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [emailType, setEmailType] = useState<'REMINDER' | 'CANCELLATION'>('REMINDER')
  const [simulatedToday, setSimulatedToday] = useState(todayInVietnam())
  const [running, setRunning] = useState(false)
  const [runResult, setRunResult] = useState<ReminderRunResult | null>(null)

  useEffect(() => {
    let active = true

    getEmailLogs(emailType)
      .then((result) => {
        if (active) {
          setLogs(result)
          setError(null)
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(
            cause instanceof Error ? cause.message : 'Không tải được nhật ký email',
          )
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
   }, [refreshKey, emailType])
  function reload() {
    setLoading(true)
    setError(null)
    setRefreshKey((key) => key + 1)
  }

  function handleRun() {
    setRunning(true)
    setError(null)
    setRunResult(null)
    runReminders(simulatedToday || undefined)
      .then((result) => {
        setRunResult(result)
        reload()
      })
      .catch((cause: unknown) => {
        setError(
          cause instanceof Error ? cause.message : 'Không chạy được tác vụ nhắc',
        )
      })
      .finally(() => setRunning(false))
  }

  return (
    <main className="audit-page">
      <section className="audit-panel">
        <div className="audit-section-heading">
          <div>
            <span className="audit-kicker">NHẮC NHẬN PHÒNG</span>
            <h2>Chạy nhắc ngay</h2>
            <p>
              Chọn ngày "hôm nay" giả lập. Hệ thống gửi email cho các booking đã xác nhận
              có ngày nhận phòng là ngày kế tiếp.
            </p>
          </div>
        </div>

        <div className="audit-filters">
          <label>
            Ngày hôm nay (giả lập)
            <input
              type="date"
              value={simulatedToday}
              onChange={(event) => setSimulatedToday(event.target.value)}
            />
          </label>

          <div className="audit-filter-actions">
            <button
              type="button"
              className="primary-button"
              onClick={handleRun}
              disabled={running}
            >
              {running ? 'Đang chạy...' : 'Chạy nhắc ngay'}
            </button>
          </div>
        </div>

        {runResult && (
          <p className="audit-empty" role="status">
            Đã chọn {runResult.selected} booking, gửi thành công {runResult.sent}, lỗi{' '}
            {runResult.failed}.
          </p>
        )}
      </section>

      {error && (
        <div className="audit-notice error" role="alert">
          {error}
        </div>
      )}

      <section className="audit-panel">
        <div className="audit-section-heading">
          <div>
            <span className="audit-kicker">DANH SÁCH</span>
            <h2>{emailType === 'REMINDER' ? 'Email nhắc đã gửi' : 'Email huỷ booking đã gửi'}</h2>
            <p>200 email gần nhất, thời gian hiển thị theo múi giờ GMT+7.</p>
          </div>
                    <select
            aria-label="Loại email"
            value={emailType}
            onChange={(event) => {
              setLoading(true)
              setEmailType(event.target.value as 'REMINDER' | 'CANCELLATION')
            }}
          >
            <option value="REMINDER">Email nhắc nhận phòng</option>
            <option value="CANCELLATION">Email huỷ booking</option>
          </select>
          <button
            type="button"
            className="secondary-button"
            onClick={reload}
            disabled={loading}
          >
            Tải lại
          </button>
        </div>

        <div className="audit-table-wrap" aria-busy={loading}>
          <table className="audit-table">
            <thead>
              <tr>
                <th>Mã booking</th>
                <th>Người nhận</th>
                <th>Thời điểm gửi (GMT+7)</th>
                <th>Kết quả</th>
                <th>Ghi chú lỗi</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((entry) => (
                <tr key={entry.id}>
                  <td>
                    <span className="audit-email">
                      {entry.bookingCode ?? `#${entry.bookingId}`}
                    </span>
                  </td>
                  <td>{entry.recipientEmail ?? 'Không có email'}</td>
                  <td>{formatDateTime(entry.sentAt)}</td>
                  <td>
                    <span
                      className={`audit-result ${
                        entry.status === 'SENT' ? 'is-success' : 'is-failure'
                      }`}
                    >
                      {entry.status === 'SENT' ? 'Đã gửi' : 'Lỗi'}
                    </span>
                  </td>
                  <td>{entry.errorMessage ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {!loading && !error && logs.length === 0 && (
            <p className="audit-empty">Chưa có email nào thuộc loại này.</p>
          )}
          {loading && <p className="audit-empty">Đang tải...</p>}
        </div>
      </section>
    </main>
  )
}