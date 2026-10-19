import { useEffect, useState } from 'react'
import {
  getEmailLogs,
  runReminders,
  getBookingReminderStatuses,
  type EmailLogItem,
  type ReminderRunResult,
  type BookingReminderPage,
} from '../services/emailLogService'
import { ReminderRunHistory } from '../components/ReminderRunHistory'
import './AuditLogPage.css'
import './ReminderEmailLogPage.css'

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
  const [emailType, setEmailType] = useState<
    'REMINDER' | 'CANCELLATION' | 'CONFIRMATION' | 'BOOKING_CREATED'
  >('REMINDER')
  const [simulatedToday, setSimulatedToday] = useState(todayInVietnam())
  const [running, setRunning] = useState(false)
  const [runResult, setRunResult] = useState<ReminderRunResult | null>(null)
  const [page, setPage] = useState(0)
  const [bookingResult, setBookingResult] = useState<{
    key: string
    data: BookingReminderPage | null
    error: string | null
  } | null>(null)

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
  const bookingRequestKey = `${page}|${refreshKey}`
  const currentBookingResult = bookingResult?.key === bookingRequestKey ? bookingResult : null
  const bookingPage = currentBookingResult?.data ?? null
  const bookingLoading = currentBookingResult === null
  const bookingError = currentBookingResult?.error ?? null

  useEffect(() => {
    let active = true

    getBookingReminderStatuses(page)
      .then((data) => {
        if (active) setBookingResult({ key: bookingRequestKey, data, error: null })
      })
      .catch((cause: unknown) => {
        if (active) {
          setBookingResult({
            key: bookingRequestKey,
            data: null,
            error: cause instanceof Error
              ? cause.message
              : 'Không tải được trạng thái nhắc',
          })
        }
      })

    return () => {
      active = false
    }
  }, [page, refreshKey, bookingRequestKey])
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
    <main className="audit-page reminder-email-page">
      <ReminderRunHistory refreshKey={refreshKey} />
      <section className="audit-panel reminder-run-now">
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
            <section className="audit-panel reminder-booking-status">
        <div className="audit-section-heading">
          <div>
            <span className="audit-kicker">TRẠNG THÁI NHẮC</span>
            <h2>Trạng thái theo booking</h2>
            <p>
              Bao gồm booking chưa nhắc, đã nhắc và gửi lỗi.
              Thời gian hiển thị theo GMT+7.
            </p>
          </div>
          <button
            type="button"
            className="secondary-button"
            onClick={reload}
            disabled={bookingLoading || running}
          >
            Tải lại
          </button>
        </div>

        {bookingError && (
          <div className="audit-notice error" role="alert">
            {bookingError}
          </div>
        )}

        <div className="audit-table-wrap" aria-busy={bookingLoading}>
          <table className="audit-table">
            <thead>
              <tr>
                <th>Mã booking</th>
                <th>Khách hàng</th>
                <th>Email</th>
                <th>Ngày nhận phòng</th>
                <th>Trạng thái booking</th>
                <th>Trạng thái nhắc</th>
                <th>Đã nhắc lúc (GMT+7)</th>
                <th>Thử gần nhất (GMT+7)</th>
                <th>Lỗi</th>
              </tr>
            </thead>
            <tbody>
              {!bookingLoading && !bookingError &&
                bookingPage?.content.map((item) => (
                  <tr key={item.bookingId}>
                    <td>{item.bookingCode}</td>
                    <td>{item.guestName}</td>
                    <td>{item.recipientEmail ?? 'Không có email'}</td>
                    <td>
                      {item.checkInDate.split('-').reverse().join('/')}
                    </td>
                    <td>
                      <span
                        className={`audit-result booking-status ${item.bookingStatus.toLowerCase()}`}
                      >
                        {{
                        CHO_XAC_NHAN: 'Chờ xác nhận',
                        DA_XAC_NHAN: 'Đã xác nhận',
                        DA_HUY: 'Đã huỷ',
                        DA_NHAN_PHONG: 'Đã nhận phòng',
                        DA_TRA_PHONG: 'Đã trả phòng',
                        DA_HET_HAN: 'Đã hết hạn',
                        }[item.bookingStatus] ?? item.bookingStatus}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`audit-result reminder-status ${
                          item.reminderStatus === 'SENT'
                            ? 'is-success'
                            : item.reminderStatus === 'FAILED'
                              ? 'is-failure'
                              : 'is-pending'
                        }`}
                      >
                        {item.reminderStatus === 'SENT'
                          ? 'Đã nhắc'
                          : item.reminderStatus === 'FAILED'
                            ? 'Gửi lỗi'
                            : 'Chưa nhắc'}
                      </span>
                    </td>
                    <td>
                      {item.remindedAt
                        ? formatDateTime(item.remindedAt)
                        : '—'}
                    </td>
                    <td>
                      {item.lastAttemptAt
                        ? formatDateTime(item.lastAttemptAt)
                        : '—'}
                    </td>
                    <td>{item.errorMessage ?? ''}</td>
                  </tr>
                ))}
            </tbody>
          </table>

          {bookingLoading && (
            <p className="audit-empty">Đang tải trạng thái...</p>
          )}

          {!bookingLoading && !bookingError &&
            bookingPage?.content.length === 0 && (
              <p className="audit-empty">Chưa có booking.</p>
            )}
        </div>

        <div className="audit-filter-actions">
          <button
            type="button"
            className="secondary-button"
            disabled={bookingLoading || !bookingPage || bookingPage.first}
            onClick={() => setPage((value) => Math.max(0, value - 1))}
          >
            Trang trước
          </button>

          <span>
            Trang {bookingPage && bookingPage.totalPages > 0 ? page + 1 : 0}
            /{bookingPage?.totalPages ?? 0}
          </span>

          <button
            type="button"
            className="secondary-button"
            disabled={bookingLoading || !bookingPage || bookingPage.last}
            onClick={() => setPage((value) => value + 1)}
          >
            Trang sau
          </button>
        </div>
      </section>

      <section className="audit-panel">
        <div className="audit-section-heading">
          <div>
            <span className="audit-kicker">DANH SÁCH</span>
            <h2>
              {emailType === 'REMINDER' && 'Email nhắc đã gửi'}
              {emailType === 'CONFIRMATION' && 'Email xác nhận booking đã gửi'}
              {emailType === 'BOOKING_CREATED' && 'Email tiếp nhận đặt phòng đã gửi'}
              {emailType === 'CANCELLATION' && 'Email huỷ booking đã gửi'}
            </h2>
            <p>200 email gần nhất, thời gian hiển thị theo múi giờ GMT+7.</p>
          </div>
          <label className="reminder-email-type-control">
          <select
            aria-label="Loại email"
            value={emailType}
            onChange={(event) => {
              setLoading(true)
              setEmailType(
                event.target.value as
                  | 'REMINDER'
                  | 'CANCELLATION'
                  | 'CONFIRMATION'
                  | 'BOOKING_CREATED',
              )
            }}
          >
            <option value="REMINDER">Email nhắc nhận phòng</option>
            <option value="CONFIRMATION">Email xác nhận booking</option>
            <option value="BOOKING_CREATED">Email tiếp nhận đặt phòng</option>
            <option value="CANCELLATION">Email huỷ booking</option>
          </select>
          </label>
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
