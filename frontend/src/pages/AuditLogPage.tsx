import { useEffect, useMemo, useState } from 'react'
import {
  getAuditLogs,
  type AuditLogEntry,
  type AuditLogPage as AuditLogPageData,
} from '../services/auditLogService'
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
  const [filters, setFilters] = useState({
    from: '',
    to: '',
    account: '',
  })
  const [appliedFilters, setAppliedFilters] = useState(filters)
  const [page, setPage] = useState(0)
  const [data, setData] = useState(EMPTY_PAGE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    getAuditLogs({ ...appliedFilters, page })
      .then((result) => {
        if (active) {
          setData(result)
          setError(null)
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(
            cause instanceof Error
              ? cause.message
              : 'Không tải được nhật ký',
          )
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [appliedFilters, page])

  const stats = useMemo(() => {
    const success = data.content.filter(
      (entry) => entry.result === 'SUCCESS',
    ).length

    const failure = data.content.length - success

    return {
      total: data.totalElements,
      success,
      failure,
    }
  }, [data])

  function submitFilters(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setLoading(true)
    setError(null)
    setPage(0)
    setAppliedFilters({ ...filters })
  }

  function clearFilters() {
    const empty = {
      from: '',
      to: '',
      account: '',
    }

    setFilters(empty)
    setAppliedFilters(empty)
    setPage(0)
    setLoading(true)
    setError(null)
  }

  function changePage(nextPage: number) {
    setLoading(true)
    setError(null)
    setPage(nextPage)
  }

  return (
    <main className="audit-page">
      <section className="audit-heading">
        <div>
          <span className="audit-kicker">THEO DÕI HỆ THỐNG</span>
          <h1>Nhật ký hoạt động</h1>
          <p>
            Theo dõi thao tác tài khoản, kết quả thực hiện và thông tin truy cập.
          </p>
        </div>

        <div className="audit-total">{data.totalElements} bản ghi</div>
      </section>

      <section className="audit-summary" aria-label="Tổng quan nhật ký">
        <article className="audit-summary-card">
          <span className="audit-summary-icon">≡</span>
          <div>
            <span>Tổng bản ghi</span>
            <strong>{stats.total}</strong>
          </div>
        </article>

        <article className="audit-summary-card">
          <span className="audit-summary-icon">✓</span>
          <div>
            <span>Thành công trên trang</span>
            <strong>{stats.success}</strong>
          </div>
        </article>

        <article className="audit-summary-card">
          <span className="audit-summary-icon">!</span>
          <div>
            <span>Thất bại trên trang</span>
            <strong>{stats.failure}</strong>
          </div>
        </article>
      </section>

      <section className="audit-panel">
        <div className="audit-section-heading">
          <div>
            <span className="audit-kicker">TRA CỨU</span>
            <h2>Bộ lọc nhật ký</h2>
            <p>
              Lọc theo khoảng ngày hoặc email người thao tác / tài khoản liên quan.
            </p>
          </div>
        </div>

        <form className="audit-filters" onSubmit={submitFilters}>
          <label>
            Từ ngày
            <input
              type="date"
              value={filters.from}
              onChange={(event) =>
                setFilters({
                  ...filters,
                  from: event.target.value,
                })
              }
            />
          </label>

          <label>
            Đến ngày
            <input
              type="date"
              value={filters.to}
              onChange={(event) =>
                setFilters({
                  ...filters,
                  to: event.target.value,
                })
              }
            />
          </label>

          <label className="audit-account-filter">
            Tài khoản
            <input
              type="search"
              placeholder="Email người thao tác hoặc tài khoản liên quan"
              value={filters.account}
              onChange={(event) =>
                setFilters({
                  ...filters,
                  account: event.target.value,
                })
              }
            />
          </label>

          <div className="audit-filter-actions">
            <button
              className="primary-button"
              type="submit"
              disabled={loading}
            >
              Lọc
            </button>

            <button
              className="secondary-button"
              type="button"
              onClick={clearFilters}
              disabled={loading}
            >
              Xoá bộ lọc
            </button>
          </div>
        </form>
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
            <h2>Lịch sử thao tác</h2>
            <p>
              Thời gian hiển thị theo múi giờ GMT+7.
            </p>
          </div>

          <span className="audit-page-count">
            Trang {data.totalPages === 0 ? 0 : page + 1}/{data.totalPages}
          </span>
        </div>

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
                  <td>
                    <span className="audit-email">
                      {entry.actorEmail ?? 'Không xác định'}
                    </span>
                  </td>
                  <td>
                    <span className="audit-email subtle">
                      {entry.accountEmail ?? 'Không xác định'}
                    </span>
                  </td>
                  <td>
                    <span className="audit-action">
                      {actionLabel(entry.action)}
                    </span>
                  </td>
                  <td>
                    <span
                      className={`audit-result ${
                        entry.result === 'SUCCESS'
                          ? 'is-success'
                          : 'is-failure'
                      }`}
                    >
                      {entry.result === 'SUCCESS'
                        ? 'Thành công'
                        : 'Thất bại'}
                    </span>
                  </td>
                  <td>
                    <span className="audit-ip">
                      {entry.ipAddress ?? 'Không xác định'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {!loading && !error && data.content.length === 0 && (
            <p className="audit-empty">
              Không có bản ghi phù hợp.
            </p>
          )}

          {loading && (
            <p className="audit-empty">
              Đang tải nhật ký...
            </p>
          )}
        </div>

        <footer className="audit-pagination">
          <span>
            Trang {data.totalPages === 0 ? 0 : page + 1} / {data.totalPages}
          </span>

          <div>
            <button
              type="button"
              className="secondary-button"
              disabled={page === 0 || loading}
              onClick={() => changePage(page - 1)}
            >
              Trước
            </button>

            <button
              type="button"
              className="secondary-button"
              disabled={page + 1 >= data.totalPages || loading}
              onClick={() => changePage(page + 1)}
            >
              Sau
            </button>
          </div>
        </footer>
      </section>
    </main>
  )
}
