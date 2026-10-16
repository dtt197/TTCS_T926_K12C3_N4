import { useEffect, useState } from 'react'
import {
  getReminderRuns,
  getReminderRunEmails,
  type ReminderRun,
  type RunEmailItem,
  type RunPage,
} from '../services/reminderRunService'

function timeVN(value: string | null) {
  if (!value) return '—'

  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'medium',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(value))
}

function dateVN(value: string) {
  return value.split('-').reverse().join('/')
}

const statusLabels: Record<ReminderRun['status'], string> = {
  RUNNING: 'Đang chạy',
  COMPLETED: 'Hoàn tất',
  COMPLETED_WITH_ERRORS: 'Hoàn tất, có lỗi gửi',
  FAILED: 'Dừng vì lỗi',
}

export function ReminderRunHistory({
  refreshKey,
}: {
  refreshKey: number
}) {
  const [page, setPage] = useState(0)
  const [runsResult, setRunsResult] = useState<{
    key: string
    data: RunPage<ReminderRun> | null
    error: string | null
  } | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const [selectedRun, setSelectedRun] = useState<ReminderRun | null>(null)
  const [emailPage, setEmailPage] = useState(0)
  const [emailResult, setEmailResult] = useState<{
    key: string
    data: RunPage<RunEmailItem> | null
    error: string | null
  } | null>(null)

  const runsRequestKey = `${page}|${refreshKey}|${reloadKey}`
  const currentRunsResult = runsResult?.key === runsRequestKey ? runsResult : null
  const runs = currentRunsResult?.data ?? null
  const loading = currentRunsResult === null
  const error = currentRunsResult?.error ?? null
  useEffect(() => {
    let active = true

    getReminderRuns(page)
      .then((data) => {
        if (active) setRunsResult({ key: runsRequestKey, data, error: null })
      })
      .catch((cause: unknown) => {
        if (active) {
          setRunsResult({
            key: runsRequestKey,
            data: null,
            error: cause instanceof Error ? cause.message : 'Không tải được lịch sử',
          })
        }
      })

    return () => { active = false }
  }, [page, refreshKey, reloadKey, runsRequestKey])

  const selectedRunId = selectedRun?.id
  const emailRequestKey = selectedRunId === undefined
    ? null
    : `${selectedRunId}|${emailPage}|${refreshKey}|${reloadKey}`
  const currentEmailResult = emailRequestKey !== null && emailResult?.key === emailRequestKey
    ? emailResult
    : null
  const emails = currentEmailResult?.data ?? null
  const emailLoading = selectedRunId !== undefined && currentEmailResult === null
  const emailError = currentEmailResult?.error ?? null

  useEffect(() => {
    if (selectedRunId === undefined || emailRequestKey === null) return

    let active = true

    getReminderRunEmails(selectedRunId, emailPage)
      .then((data) => {
        if (active) setEmailResult({ key: emailRequestKey, data, error: null })
      })
      .catch((cause: unknown) => {
        if (active) {
          setEmailResult({
            key: emailRequestKey,
            data: null,
            error: cause instanceof Error ? cause.message : 'Không tải được email',
          })
        }
      })

    return () => { active = false }
  }, [selectedRunId, emailPage, refreshKey, reloadKey, emailRequestKey])

  return (
    <section className="audit-panel">
      <div className="audit-section-heading">
        <div>
          <span className="audit-kicker">LỊCH SỬ TÁC VỤ</span>
          <h2>Các lần chạy nhắc nhận phòng</h2>
          <p>Mới nhất ở trên. Giờ hiển thị: Asia/Ho_Chi_Minh (GMT+7).</p>
        </div>
        <button
          type="button"
          className="secondary-button"
          disabled={loading}
          onClick={() => setReloadKey((value) => value + 1)}
        >
          Tải lại lịch sử
        </button>
      </div>

      {error && <p role="alert" className="audit-notice error">{error}</p>}
      {loading && <p role="status">Đang tải lịch sử...</p>}

      {!loading && !error && (
        <>
          <div className="audit-table-wrap">
            <table className="audit-table">
              <thead>
                <tr>
                  <th>Lượt</th>
                  <th>Bắt đầu (GMT+7)</th>
                  <th>Kết thúc (GMT+7)</th>
                  <th>Ngày nhận phòng</th>
                  <th>Kích hoạt</th>
                  <th>Trạng thái</th>
                  <th>Đã chọn</th>
                  <th>Đã gửi</th>
                  <th>Bỏ qua</th>
                  <th>Lỗi gửi</th>
                  <th>Lỗi lượt chạy</th>
                  <th>Chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {runs?.content.map((run) => (
                  <tr key={run.id}>
                    <td>#{run.id}</td>
                    <td>{timeVN(run.startedAt)}</td>
                    <td>{timeVN(run.finishedAt)}</td>
                    <td>{dateVN(run.checkInDate)}</td>
                    <td>{run.triggerType === 'SCHEDULED' ? 'Định kỳ' : 'Thủ công'}</td>
                    <td>{statusLabels[run.status]}</td>
                    <td>{run.selectedCount}</td>
                    <td>{run.sentCount}</td>
                    <td>{run.skippedCount}</td>
                    <td>{run.failedCount}</td>
                    <td>{run.errorMessage ?? '—'}</td>
                    <td>
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() => {
                          setSelectedRun(run)
                          setEmailPage(0)
                        }}
                      >
                        Xem email
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {runs?.content.length === 0 && (
            <p className="audit-empty">Chưa có lượt chạy nào.</p>
          )}

          <div className="audit-filter-actions">
            <button
              type="button"
              disabled={!runs || runs.first}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
            >
              Trang trước
            </button>
            <span>
              Trang {runs && runs.totalPages > 0 ? page + 1 : 0}
              /{runs?.totalPages ?? 0}
            </span>
            <button
              type="button"
              disabled={!runs || runs.last}
              onClick={() => setPage((value) => value + 1)}
            >
              Trang sau
            </button>
          </div>
        </>
      )}

      {selectedRun && (
        <section aria-label={`Email của lượt ${selectedRun.id}`}>
          <h3>Email gửi thành công của lượt #{selectedRun.id}</h3>

          <button type="button" onClick={() => setSelectedRun(null)}>
            Đóng chi tiết
          </button>

          {emailError && <p role="alert">{emailError}</p>}
          {emailLoading && <p role="status">Đang tải email...</p>}

          {!emailLoading && !emailError && emails && (
            <>
              <p>Tổng số email trong danh sách: {emails.totalElements}.</p>
              <div className="audit-table-wrap">
                <table className="audit-table">
                  <thead>
                    <tr>
                      <th>ID booking</th>
                      <th>Người nhận</th>
                      <th>Thời điểm gửi (GMT+7)</th>
                      <th>Kết quả</th>
                    </tr>
                  </thead>
                  <tbody>
                    {emails.content.map((email) => (
                      <tr key={email.id}>
                        <td>#{email.bookingId}</td>
                        <td>{email.recipientEmail ?? '—'}</td>
                        <td>{timeVN(email.sentAt)}</td>
                        <td>Đã gửi</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {emails.totalElements === 0 && (
                <p>Không có email gửi thành công trong lượt này.</p>
              )}

              <div className="audit-filter-actions">
                <button
                  type="button"
                  disabled={emails.first}
                  onClick={() => setEmailPage((value) => Math.max(0, value - 1))}
                >
                  Trang trước
                </button>
                <span>
                  Trang {emails.totalPages > 0 ? emailPage + 1 : 0}
                  /{emails.totalPages}
                </span>
                <button
                  type="button"
                  disabled={emails.last}
                  onClick={() => setEmailPage((value) => value + 1)}
                >
                  Trang sau
                </button>
              </div>
            </>
          )}
        </section>
      )}
    </section>
  )
}