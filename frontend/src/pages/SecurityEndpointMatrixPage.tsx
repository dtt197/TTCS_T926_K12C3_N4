import { useCallback, useEffect, useMemo, useState } from 'react'
import { createEndpointDiscrepancy, getEndpointDiscrepancies } from '../services/endpointDiscrepancyService'
import { apiRequest } from '../services/apiClient'
import { getUsers } from '../services/userService'
import {
  compareEndpointPermissions,
  filterEndpointPermissionComparisons,
  getEndpointFollowUpStatus,
  ticketMatchesCurrentMismatch,
} from '../permissions/compareEndpointPermissions'
import type {
  CreateEndpointDiscrepancyPayload,
  EndpointDiscrepancyTicket,
  EndpointPermission,
  EndpointPermissionComparison,
  Role,
} from '../types/endpointPermission'
import type { StaffUser } from '../types/user'
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
  const [tickets, setTickets] = useState<EndpointDiscrepancyTicket[]>([])
  const [users, setUsers] = useState<StaffUser[]>([])
  const [activeTicketKey, setActiveTicketKey] = useState<string | null>(null)
  const [assigneeId, setAssigneeId] = useState('')
  const [description, setDescription] = useState('')
  const [ticketError, setTicketError] = useState<string | null>(null)
  const [submittingKey, setSubmittingKey] = useState<string | null>(null)

  const requestEndpoints = useCallback(() => (
    apiRequest<EndpointPermission[]>('/api/admin/security/endpoint-permissions')
  ), [])
  const requestTickets = useCallback(() => getEndpointDiscrepancies(), [])
  const requestUsers = useCallback(() => getUsers(), [])

  const loadEndpoints = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const [endpointResult, ticketResult, userResult] = await Promise.all([
        requestEndpoints(),
        requestTickets(),
        requestUsers(),
      ])
      setEndpoints(endpointResult)
      setTickets(ticketResult)
      setUsers(userResult)
    } catch (loadError) {
      setError(loadError instanceof Error
        ? loadError.message
        : 'Không thể tải ma trận quyền endpoint.')
    } finally {
      setIsLoading(false)
    }
  }, [requestEndpoints, requestTickets, requestUsers])

  useEffect(() => {
    let isActive = true
    Promise.all([requestEndpoints(), requestTickets(), requestUsers()])
      .then(([endpointResult, ticketResult, userResult]) => {
        if (isActive) {
          setEndpoints(endpointResult)
          setTickets(ticketResult)
          setUsers(userResult)
        }
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
  }, [requestEndpoints, requestTickets, requestUsers])

  const comparisons = useMemo(
    () => compareEndpointPermissions(endpoints),
    [endpoints],
  )
  const mismatchCount = comparisons.filter((item) => item.hasMismatch).length
  const ticketsByEndpoint = useMemo(() => {
    const result = new Map<string, EndpointDiscrepancyTicket>()
    tickets.forEach((ticket) => {
      const key = endpointKey(ticket.method, ticket.path)
      if (!result.has(key)) result.set(key, ticket)
    })
    return result
  }, [tickets])
  const unrecordedCount = comparisons.filter((item) => (
    item.hasMismatch
    && !ticketMatchesCurrentMismatch(
      ticketsByEndpoint.get(endpointKey(item.endpoint.method, item.endpoint.path)),
      item,
    )
  )).length
  const filteredComparisons = filterEndpointPermissionComparisons(comparisons, {
    mismatchesOnly: filter === 'mismatches',
    role: roleFilter,
    query,
  })

  const startTicket = (item: EndpointPermissionComparison) => {
    setActiveTicketKey(endpointKey(item.endpoint.method, item.endpoint.path))
    setAssigneeId('')
    setDescription('')
    setTicketError(null)
  }

  const submitTicket = async (item: EndpointPermissionComparison) => {
    const key = endpointKey(item.endpoint.method, item.endpoint.path)
    const selectedAssigneeId = Number(assigneeId)
    if (!selectedAssigneeId || !description.trim()) {
      setTicketError('Chọn người phụ trách và nhập nội dung khiếm khuyết.')
      return
    }

    const payload: CreateEndpointDiscrepancyPayload = {
      method: item.endpoint.method,
      path: item.endpoint.path,
      approvedAccess: item.approved.access,
      approvedRoles: item.approved.roles,
      assigneeId: selectedAssigneeId,
      description: description.trim(),
    }

    setSubmittingKey(key)
    setTicketError(null)
    try {
      const createdTicket = await createEndpointDiscrepancy(payload)
      setTickets((current) => [createdTicket, ...current])
      setActiveTicketKey(null)
      setAssigneeId('')
      setDescription('')
    } catch (submitError) {
      setTicketError(submitError instanceof Error
        ? submitError.message
        : 'Không thể tạo phiếu ghi nhận khiếm khuyết.')
    } finally {
      setSubmittingKey(null)
    }
  }

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
          <span>Tổng chênh lệch hiện tại</span>
          <strong>{isLoading ? '—' : mismatchCount}</strong>
        </article>
        <article>
          <span>Khớp ma trận</span>
          <strong>{isLoading ? '—' : comparisons.length - mismatchCount}</strong>
        </article>
        <article className={unrecordedCount ? 'has-mismatch' : 'is-matched'}>
          <span>Chưa có người phụ trách</span>
          <strong>{isLoading ? '—' : unrecordedCount}</strong>
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
              <th scope="col">Theo dõi xử lý</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={5} className="endpoint-matrix-empty">Đang tải quyền endpoint…</td></tr>
            ) : filteredComparisons.map((item) => (
              <EndpointRow
                key={endpointKey(item.endpoint.method, item.endpoint.path)}
                item={item}
                ticket={ticketsByEndpoint.get(endpointKey(item.endpoint.method, item.endpoint.path))}
                hasTicketForCurrentMismatch={ticketMatchesCurrentMismatch(
                  ticketsByEndpoint.get(endpointKey(item.endpoint.method, item.endpoint.path)),
                  item,
                )}
                users={users}
                activeTicketKey={activeTicketKey}
                assigneeId={assigneeId}
                description={description}
                ticketError={ticketError}
                isSubmitting={submittingKey === endpointKey(item.endpoint.method, item.endpoint.path)}
                onStartTicket={() => startTicket(item)}
                onAssigneeChange={setAssigneeId}
                onDescriptionChange={setDescription}
                onSubmit={() => void submitTicket(item)}
                onCancel={() => {
                  setActiveTicketKey(null)
                  setTicketError(null)
                }}
              />
            ))}
            {!isLoading && !error && filteredComparisons.length === 0 && (
              <tr>
                <td colSpan={5} className="endpoint-matrix-empty">
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

function EndpointRow({
  item,
  ticket,
  hasTicketForCurrentMismatch,
  users,
  activeTicketKey,
  assigneeId,
  description,
  ticketError,
  isSubmitting,
  onStartTicket,
  onAssigneeChange,
  onDescriptionChange,
  onSubmit,
  onCancel,
}: {
  item: EndpointPermissionComparison
  ticket: EndpointDiscrepancyTicket | undefined
  hasTicketForCurrentMismatch: boolean
  users: StaffUser[]
  activeTicketKey: string | null
  assigneeId: string
  description: string
  ticketError: string | null
  isSubmitting: boolean
  onStartTicket: () => void
  onAssigneeChange: (value: string) => void
  onDescriptionChange: (value: string) => void
  onSubmit: () => void
  onCancel: () => void
}) {
  const key = endpointKey(item.endpoint.method, item.endpoint.path)
  const isFormOpen = activeTicketKey === key
  const activeUsers = users.filter((user) => user.active)
  const statusLabels = {
    UNRECORDED: 'Chưa ghi nhận',
    RECORDED: 'Đã ghi nhận',
    RESOLVED: 'Đã khắc phục',
    MATCHED: 'Khớp',
  } as const
  const followUpStatus = getEndpointFollowUpStatus(
    item.hasMismatch,
    hasTicketForCurrentMismatch,
    ticket !== undefined,
  )

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
          {item.hasMismatch ? 'Cần xử lý' : 'Khớp'}
        </span>
      </td>
      <td className="endpoint-follow-up">
        <span className={`endpoint-follow-up-status ${
          item.hasMismatch && !hasTicketForCurrentMismatch ? 'pending' : ''
        }`}>
          {statusLabels[followUpStatus]}
        </span>
        {ticket && (
          <div className="endpoint-ticket-summary">
            <strong>Phụ trách: {ticket.assigneeName}</strong>
            <span>{ROLE_LABELS[ticket.assigneeRole]}</span>
            <p>{ticket.description}</p>
            <small>Ghi nhận bởi {ticket.createdByName}</small>
          </div>
        )}
        {item.hasMismatch && !hasTicketForCurrentMismatch && !isFormOpen && (
          <button className="secondary-button endpoint-record-button" type="button" onClick={onStartTicket}>
            Ghi nhận khiếm khuyết
          </button>
        )}
        {isFormOpen && (
          <div className="endpoint-ticket-form">
            <label>
              Người phụ trách
              <select
                value={assigneeId}
                onChange={(event) => onAssigneeChange(event.target.value)}
                disabled={isSubmitting}
              >
                <option value="">Chọn nhân viên</option>
                {activeUsers.map((user) => (
                  <option key={user.id} value={user.id}>
                    {user.fullName} — {ROLE_LABELS[user.role]}
                  </option>
                ))}
              </select>
            </label>
            {activeUsers.length === 0 && <small>Không có tài khoản hoạt động để giao việc.</small>}
            <label>
              Mô tả khiếm khuyết
              <textarea
                value={description}
                onChange={(event) => onDescriptionChange(event.target.value)}
                maxLength={2000}
                rows={3}
                disabled={isSubmitting}
                placeholder="Mô tả sai lệch và việc cần xử lý"
              />
            </label>
            {ticketError && <span className="endpoint-ticket-error" role="alert">{ticketError}</span>}
            <div className="endpoint-ticket-actions">
              <button
                className="primary-button"
                type="button"
                onClick={onSubmit}
                disabled={isSubmitting || activeUsers.length === 0}
              >
                {isSubmitting ? 'Đang lưu…' : 'Tạo phiếu'}
              </button>
              <button
                className="secondary-button"
                type="button"
                onClick={onCancel}
                disabled={isSubmitting}
              >
                Hủy
              </button>
            </div>
          </div>
        )}
      </td>
    </tr>
  )
}

function endpointKey(method: string, path: string) {
  return `${method} ${path}`
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
