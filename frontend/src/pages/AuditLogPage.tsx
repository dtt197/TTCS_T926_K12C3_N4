import { useEffect, useState } from 'react'
import { getAuditLogs, type AuditLogEntry, type AuditLogPage as AuditLogPageData } from '../services/auditLogService'
import './AuditLogPage.css'

const EMPTY_PAGE: AuditLogPageData = {
  content: [],
  page: 0,
  size: 50,
  totalElements: 0,
  totalPages: 0,
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(value))
}

function actionLabel(action: string) {
  const labels: Record<string, string> = {
    LOGIN: 'Đăng nhập',
    ROLE_CHANGED: 'Đổi vai trò',
    ACCOUNT_DISABLED: 'Vô hiệu hóa tài khoản',
    GUEST_DOCUMENT_VIEWED: 'Xem giấy tờ khách',
  }
  return labels[action] ?? action
}

export function AuditLogPage() {
  const [filters, setFilters] = useState({ from: '', to: '', account: '' })
  const [appliedFilters, setAppliedFilters] = useState(filters)
  const [page, setPage] = useState(0)
  const [data, setData] = useState(EMPTY_PAGE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    getAuditLogs({ ...appliedFilters, page })
      .then((result) => {
        if (active) setData(result)
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : 'Không tải được nhật ký')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [appliedFilters, page])

  function submitFilters(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setLoading(true)
    setError(null)
    setPage(0)
    setAppliedFilters({ ...filters })
  }

  function changePage(nextPage: number) {
    setLoading(true)
    setError(null)
    setPage(nextPage)
  }

  return (
    <main className="audit-page">
      <header className="audit-heading">
        <div>

          <h1>Nhật ký hoạt động</h1>
        </div>
        <p className="audit-total">{data.totalElements} bản ghi</p>
      </header>

      <form className="audit-filters" onSubmit={submitFilters}>
        <label>
          Từ ngày
          <input type="date" value={filters.from} onChange={(event) => setFilters({ ...filters, from: event.target.value })} />
        </label>
        <label>
          Đến ngày
          <input type="date" value={filters.to} onChange={(event) => setFilters({ ...filters, to: event.target.value })} />
        </label>
        <label className="audit-account-filter">
          Tài khoản
          <input type="search" placeholder="Email người thao tác hoặc tài khoản liên quan" value={filters.account} onChange={(event) => setFilters({ ...filters, account: event.target.value })} />
        </label>
        <button className="primary-button" type="submit">Lọc</button>
      </form>

      {error && <div className="alert" role="alert">{error}</div>}

      <div className="audit-table-wrap" aria-busy={loading}>
        <table className="audit-table">
          <thead>
            <tr>
              <th>Thời gian (GMT+7)</th>
              <th>Người thao tác</th>
              <th>Tài khoản liên quan</th>
              <th>Hành động</th>
              <th>Kết quả</th>
              <th>IP</th>
            </tr>
          </thead>
          <tbody>
            {data.content.map((entry: AuditLogEntry) => (
              <tr key={entry.id}>
                <td>{formatDate(entry.occurredAt)}</td>
                <td>{entry.actorEmail ?? 'Không xác định'}</td>
                <td>{entry.accountEmail ?? 'Không xác định'}</td>
                <td>{actionLabel(entry.action)}</td>
                <td><span className={`audit-result ${entry.result === 'SUCCESS' ? 'is-success' : 'is-failure'}`}>
                  {entry.result === 'SUCCESS' ? 'Thành công' : 'Thất bại'}
                </span></td>
                <td>{entry.ipAddress ?? 'Không xác định'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && !error && data.content.length === 0 && <p className="audit-empty">Không có bản ghi phù hợp.</p>}
        {loading && <p className="audit-empty">Đang tải nhật ký...</p>}
      </div>

      <footer className="audit-pagination">
        <span>Trang {data.totalPages === 0 ? 0 : page + 1} / {data.totalPages}</span>
        <div>
          <button type="button" className="secondary-button" disabled={page === 0 || loading} onClick={() => changePage(page - 1)}>Trước</button>
          <button type="button" className="secondary-button" disabled={page + 1 >= data.totalPages || loading} onClick={() => changePage(page + 1)}>Sau</button>
        </div>
      </footer>
    </main>
  )
}