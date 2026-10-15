import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  compareEndpointPermissions,
  filterEndpointPermissionComparisons,
} from '../permissions/compareEndpointPermissions'
import type {
  EndpointPermission,
  EndpointPermissionComparison,
  Role,
} from '../types/endpointPermission'
import { apiRequest } from '../services/apiClient'
import './SecurityEndpointMatrixPage.css'

const ROLES: readonly Role[] = [
  'ADMIN',
  'OWNER',
  'RECEPTIONIST',
  'HOUSEKEEPING',
]

const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Quản trị',
  OWNER: 'Chủ homestay',
  RECEPTIONIST: 'Lễ tân',
  HOUSEKEEPING: 'Buồng phòng',
}

type ReviewFilter = 'all' | 'mismatches'

export function SecurityEndpointMatrixPage() {
  const [endpoints, setEndpoints] = useState<EndpointPermission[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<ReviewFilter>('all')
  const [roleFilter, setRoleFilter] = useState<Role | 'ALL'>('ALL')
  const [query, setQuery] = useState('')

  const requestEndpoints = useCallback(() => (
    apiRequest<EndpointPermission[]>('/api/admin/security/endpoint-permissions')
  ), [])

  const loadEndpoints = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const result = await requestEndpoints()
      setEndpoints(result)
    } catch (loadError) {
      setError(loadError instanceof Error
        ? loadError.message
        : 'Không thể tải ma trận quyền endpoint.')
    } finally {
      setIsLoading(false)
    }
  }, [requestEndpoints])

  useEffect(() => {
    let isActive = true
    requestEndpoints()
      .then((result) => {
        if (isActive) setEndpoints(result)
      })
      .catch((loadError: unknown) => {
        if (isActive) {
          setError(loadError instanceof Error
            ? loadError.message
            : 'Không thể tải ma trận quyền endpoint.')
        }
      })
      .finally(() => {
        if (isActive) setIsLoading(false)
      })

    return () => {
      isActive = false
    }
  }, [requestEndpoints])

  const comparisons = useMemo(
    () => compareEndpointPermissions(endpoints),
    [endpoints],
  )
  const mismatchCount = comparisons.filter((item) => item.hasMismatch).length
  const filteredComparisons = filterEndpointPermissionComparisons(comparisons, {
    mismatchesOnly: filter === 'mismatches',
    role: roleFilter,
    query,
  })

  return (
    <section className="endpoint-matrix-page">
      <div className="endpoint-matrix-heading">
        <div>
          <p className="eyebrow">BẢO MẬT API</p>
          <h2>Đối chiếu ma trận phân quyền</h2>
          <p>
            So sánh quyền đã duyệt với cấu hình bảo vệ đang áp dụng cho từng endpoint.
            Màn hình chỉ phát hiện và báo cáo chênh lệch, không tự thay đổi quyền.
          </p>
        </div>
        <button
          className="secondary-button"
          type="button"
          onClick={() => void loadEndpoints()}
          disabled={isLoading}
        >
          {isLoading ? 'Đang tải…' : 'Làm mới dữ liệu'}
        </button>
      </div>

      {error && (
        <div className="endpoint-matrix-error" role="alert">
          <span>{error}</span>
          <button type="button" className="secondary-button" onClick={() => void loadEndpoints()}>
            Thử lại
          </button>
        </div>
      )}

      <div className="endpoint-matrix-summary" aria-live="polite">
        <article>
          <span>Tổng endpoint</span>
          <strong>{isLoading ? '—' : comparisons.length}</strong>
        </article>
        <article className={mismatchCount ? 'has-mismatch' : 'is-matched'}>
          <span>Chênh lệch cần rà soát</span>
          <strong>{isLoading ? '—' : mismatchCount}</strong>
        </article>
        <article>
          <span>Khớp ma trận</span>
          <strong>{isLoading ? '—' : comparisons.length - mismatchCount}</strong>
        </article>
      </div>

      <div className="endpoint-matrix-filters">
        <label>
          Tìm endpoint
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Ví dụ: GET /api/bookings"
          />
        </label>
        <label>
          Vai trò
          <select
            value={roleFilter}
            onChange={(event) => setRoleFilter(event.target.value as Role | 'ALL')}
          >
            <option value="ALL">Tất cả vai trò</option>
            {ROLES.map((role) => (
              <option key={role} value={role}>{ROLE_LABELS[role]}</option>
            ))}
          </select>
        </label>
        <label>
          Trạng thái
          <select
            value={filter}
            onChange={(event) => setFilter(event.target.value as ReviewFilter)}
          >
            <option value="all">Tất cả endpoint</option>
            <option value="mismatches">Chỉ chênh lệch</option>
          </select>
        </label>
      </div>

      <div className="endpoint-matrix-table-wrap">
        <table className="endpoint-matrix-table">
          <thead>
            <tr>
              <th scope="col">Điểm cuối API</th>
              <th scope="col">Ma trận đã duyệt</th>
              <th scope="col">Quyền thực tế</th>
              <th scope="col">Đánh giá</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={4} className="endpoint-matrix-empty">Đang tải quyền endpoint…</td></tr>
            ) : filteredComparisons.map((item) => (
              <EndpointRow key={`${item.endpoint.method} ${item.endpoint.path}`} item={item} />
            ))}
            {!isLoading && !error && filteredComparisons.length === 0 && (
              <tr>
                <td colSpan={4} className="endpoint-matrix-empty">
                  {comparisons.length === 0
                    ? 'Không tìm thấy endpoint API nào để đối chiếu.'
                    : 'Không có endpoint phù hợp với bộ lọc.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function EndpointRow({ item }: { item: EndpointPermissionComparison }) {
  return (
    <tr className={item.hasMismatch ? 'endpoint-mismatch' : undefined}>
      <td>
        <code className="endpoint-method">{item.endpoint.method}</code>
        <code className="endpoint-path">{item.endpoint.path}</code>
      </td>
      <td><PermissionSummary roles={item.approved.roles} access={item.approved.access} /></td>
      <td><PermissionSummary roles={item.endpoint.roles} access={item.endpoint.access} /></td>
      <td>
        <span className={item.hasMismatch ? 'endpoint-status mismatch' : 'endpoint-status matched'}>
          {item.hasMismatch ? 'Cần rà soát' : 'Khớp'}
        </span>
      </td>
    </tr>
  )
}

function PermissionSummary({
  roles,
  access,
}: {
  roles: readonly Role[]
  access: EndpointPermission['access']
}) {
  const accessText = access === 'PUBLIC'
    ? 'Công khai'
    : access === 'AUTHENTICATED'
      ? 'Đã đăng nhập'
      : access === 'DENIED'
        ? 'Từ chối'
        : ''

  return (
    <div className="endpoint-permission-summary">
      {accessText && <span className={`endpoint-access ${access.toLowerCase()}`}>{accessText}</span>}
      {roles.length > 0
        ? roles.map((role) => (
          <span className="endpoint-role" key={role}>{ROLE_LABELS[role]}</span>
        ))
        : !accessText && <span className="endpoint-no-roles">Không có vai trò được cấp</span>}
    </div>
  )
}
