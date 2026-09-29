import { useEffect, useMemo, useState } from 'react'
import '../App.css'
import './UserManagementPage.css'
import { CreateUserForm } from '../components/CreateUserForm'
import { EditUserForm } from '../components/EditUserForm'
import { hasPermission } from '../permissions/rolePermissions'
import {
  createUser,
  getUsers,
  resendTemporaryPassword,
  updateUser,
  updateUserStatus,
} from '../services/userService'
import {
  ROLE_LABELS,
  type CreateUserPayload,
  type StaffUser,
  type UpdateUserPayload,
} from '../types/user'

type UserManagementPageProps = {
  currentUserId: number
  role: string
}

type Notice = {
  type: 'success' | 'error'
  text: string
}

const PAGE_SIZE = 20

/** S1-04: ADMIN quản lý tài khoản, OWNER chỉ xem. */
export function UserManagementPage({
  currentUserId,
  role,
}: UserManagementPageProps) {
  const canViewAccounts = hasPermission(role, 'accounts:view')
  const canManageAccounts = hasPermission(role, 'accounts:manage')

  const [users, setUsers] = useState<StaffUser[]>([])
  const [isLoading, setIsLoading] = useState(canViewAccounts)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [busyUserId, setBusyUserId] = useState<number | null>(null)
  const [editingUser, setEditingUser] = useState<StaffUser | null>(null)
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)

  useEffect(() => {
    if (!canViewAccounts) return

    getUsers()
      .then(setUsers)
      .catch((err: unknown) =>
        setLoadError(
          err instanceof Error
            ? err.message
            : 'Không tải được danh sách tài khoản',
        ),
      )
      .finally(() => setIsLoading(false))
  }, [canViewAccounts])

  const search = keyword.trim().toLowerCase()

  const filteredUsers = users.filter((u) =>
    `${u.fullName} ${u.email}`.toLowerCase().includes(search),
  )

  const totalPages = Math.max(
    1,
    Math.ceil(filteredUsers.length / PAGE_SIZE),
  )

  const currentPage = Math.min(page, totalPages - 1)

  const visibleUsers = filteredUsers.slice(
    currentPage * PAGE_SIZE,
    (currentPage + 1) * PAGE_SIZE,
  )

  const stats = useMemo(
    () => ({
      total: users.length,
      active: users.filter((user) => user.active).length,
      mustChangePassword: users.filter((user) => user.mustChangePassword).length,
    }),
    [users],
  )

  function replaceUser(updated: StaffUser) {
    setUsers((current) =>
      current.map((u) => (u.id === updated.id ? updated : u)),
    )
  }

  async function handleCreate(payload: CreateUserPayload) {
    if (!canManageAccounts) return

    const created = await createUser(payload)

    setUsers((current) => [created, ...current])

    setNotice({
      type: 'success',
      text: `Đã tạo tài khoản ${created.email}. Mật khẩu tạm đã được gửi qua email.`,
    })
  }

  async function handleUpdate(payload: UpdateUserPayload) {
    if (!canManageAccounts || !editingUser) return

    const roleChanged = payload.role !== editingUser.role
    const updated = await updateUser(editingUser.id, payload)

    replaceUser(updated)
    setEditingUser(null)

    setNotice({
      type: 'success',
      text: roleChanged
        ? `Đã cập nhật ${updated.email}. Người này sẽ phải đăng nhập lại để nhận vai trò mới.`
        : `Đã cập nhật tài khoản ${updated.email}.`,
    })
  }

  async function runAction(
    target: StaffUser,
    action: () => Promise<StaffUser>,
    successText: string,
  ) {
    if (!canManageAccounts) return

    setBusyUserId(target.id)
    setNotice(null)

    try {
      replaceUser(await action())

      setNotice({
        type: 'success',
        text: successText,
      })
    } catch (err) {
      setNotice({
        type: 'error',
        text:
          err instanceof Error
            ? err.message
            : 'Không thực hiện được thao tác',
      })
    } finally {
      setBusyUserId(null)
    }
  }

  function handleToggleActive(target: StaffUser) {
    if (!canManageAccounts) return

    const nextActive = !target.active

    const question = nextActive
      ? `Kích hoạt lại tài khoản ${target.email}?`
      : `Vô hiệu hoá tài khoản ${target.email}? Người này sẽ bị đăng xuất và không đăng nhập được nữa.`

    if (!window.confirm(question)) return

    void runAction(
      target,
      () => updateUserStatus(target.id, nextActive),
      nextActive
        ? `Đã kích hoạt lại tài khoản ${target.email}.`
        : `Đã vô hiệu hoá ${target.email}. Phiên đăng nhập của người này đã hết hiệu lực.`,
    )
  }

  function handleResend(target: StaffUser) {
    if (!canManageAccounts) return

    if (
      !window.confirm(
        `Gửi mật khẩu tạm mới tới ${target.email}? Mật khẩu tạm cũ sẽ không dùng được nữa.`,
      )
    ) {
      return
    }

    void runAction(
      target,
      () => resendTemporaryPassword(target.id),
      `Đã gửi mật khẩu tạm mới tới ${target.email}.`,
    )
  }

  if (!canViewAccounts) {
    return (
      <div className="user-management-page">
        <div className="user-notice error" role="alert">
          Bạn không có quyền xem danh sách tài khoản.
        </div>
      </div>
    )
  }

  return (
    <div className="user-management-page">
      <section className="user-management-heading">
        <div>
          <span className="user-management-kicker">QUẢN LÝ NHÂN SỰ</span>
          <h1>Tài khoản nhân viên</h1>
          <p>
            {canManageAccounts
              ? 'Mỗi nhân viên một tài khoản riêng, gán đúng một vai trò và theo dõi trạng thái truy cập.'
              : 'Xem danh sách và trạng thái tài khoản nhân viên.'}
          </p>
        </div>

        <div className="user-management-count">
          {filteredUsers.length} / {users.length} tài khoản
        </div>
      </section>

      <section className="user-summary" aria-label="Tổng quan tài khoản">
        <article className="user-summary-card">
          <span className="user-summary-icon">●</span>
          <div>
            <span>Tổng tài khoản</span>
            <strong>{stats.total}</strong>
          </div>
        </article>

        <article className="user-summary-card">
          <span className="user-summary-icon">✓</span>
          <div>
            <span>Đang hoạt động</span>
            <strong>{stats.active}</strong>
          </div>
        </article>

        <article className="user-summary-card">
          <span className="user-summary-icon">⌁</span>
          <div>
            <span>Chưa đổi mật khẩu tạm</span>
            <strong>{stats.mustChangePassword}</strong>
          </div>
        </article>
      </section>

      {notice && (
        <div
          className={`user-notice ${notice.type}`}
          role="status"
        >
          {notice.text}
        </div>
      )}

      <section
        className={
          canManageAccounts
            ? 'user-management-workspace'
            : 'user-management-workspace readonly'
        }
      >
        <div className="user-list-panel">
          <div className="user-section-heading">
            <div>
              <span className="user-management-kicker">DANH SÁCH</span>
              <h2>Danh sách tài khoản</h2>
              <p>Tìm kiếm, kiểm tra vai trò và quản lý trạng thái truy cập.</p>
            </div>

            <span className="user-list-count">
              {filteredUsers.length} tài khoản
            </span>
          </div>

          <div className="user-search-wrap">
            <span className="user-search-icon">⌕</span>
            <input
              className="form-control user-search"
              placeholder="Tìm theo họ tên hoặc email..."
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value)
                setPage(0)
              }}
            />
          </div>

          {isLoading ? (
            <div className="user-empty-state">Đang tải...</div>
          ) : loadError ? (
            <div className="user-notice error" role="alert">
              {loadError}
            </div>
          ) : visibleUsers.length === 0 ? (
            <div className="user-empty-state">
              {users.length === 0
                ? 'Chưa có tài khoản nào.'
                : 'Không có tài khoản nào khớp từ khoá.'}
            </div>
          ) : (
            <div className="user-card-grid">
              {visibleUsers.map((u) => {
                const isSelf = u.id === currentUserId

                return (
                  <article className="user-card" key={u.id}>
                    <div className="user-card-top">
                      <div className="user-card-identity">
                        <div className="user-avatar">
                          {u.fullName.trim().charAt(0).toUpperCase() || 'U'}
                        </div>

                        <div className="user-card-title">
                          <div className="user-name-row">
                            <h3>{u.fullName}</h3>
                            {isSelf && <span className="user-self-badge">Bạn</span>}
                          </div>
                          <p>{u.email}</p>
                        </div>
                      </div>

                      <span className={`user-status ${u.active ? 'active' : 'inactive'}`}>
                        {u.active ? 'Hoạt động' : 'Vô hiệu hoá'}
                      </span>
                    </div>

                    <div className="user-card-meta">
                      <div>
                        <span>Vai trò</span>
                        <strong>{ROLE_LABELS[u.role] ?? u.role}</strong>
                      </div>

                      <div>
                        <span>Điện thoại</span>
                        <strong>{u.phone ?? '—'}</strong>
                      </div>
                    </div>

                    {u.mustChangePassword && (
                      <div className="user-password-warning">
                        Chưa đổi mật khẩu tạm
                      </div>
                    )}

                    {canManageAccounts && (
                      <div className="user-card-actions">
                        <button
                          type="button"
                          className="secondary-button"
                          disabled={busyUserId === u.id}
                          onClick={() => setEditingUser(u)}
                        >
                          Sửa
                        </button>

                        {u.mustChangePassword && !isSelf && (
                          <button
                            type="button"
                            className="secondary-button"
                            disabled={busyUserId === u.id}
                            onClick={() => handleResend(u)}
                          >
                            Gửi lại mật khẩu tạm
                          </button>
                        )}

                        {!isSelf && (
                          <button
                            type="button"
                            className={`secondary-button ${u.active ? 'danger' : ''}`}
                            disabled={busyUserId === u.id}
                            onClick={() => handleToggleActive(u)}
                          >
                            {u.active ? 'Vô hiệu hoá' : 'Kích hoạt lại'}
                          </button>
                        )}
                      </div>
                    )}
                  </article>
                )
              })}
            </div>
          )}

          {totalPages > 1 && (
            <div className="user-pager">
              <button
                type="button"
                className="secondary-button"
                disabled={currentPage === 0}
                onClick={() => setPage(currentPage - 1)}
              >
                ‹ Trước
              </button>

              <span>
                Trang {currentPage + 1}/{totalPages}
              </span>

              <button
                type="button"
                className="secondary-button"
                disabled={currentPage + 1 >= totalPages}
                onClick={() => setPage(currentPage + 1)}
              >
                Sau ›
              </button>
            </div>
          )}
        </div>

        {canManageAccounts && (
          <aside className="user-form-panel">
            <div className="user-section-heading compact">
              <div>
                <span className="user-management-kicker">
                  {editingUser ? 'CHỈNH SỬA' : 'THÊM MỚI'}
                </span>

                <h2>{editingUser ? 'Sửa tài khoản' : 'Tạo tài khoản'}</h2>

                <p>Chỉ Quản trị hệ thống thực hiện được.</p>
              </div>
            </div>

            {editingUser ? (
              <EditUserForm
                key={editingUser.id}
                user={editingUser}
                isSelf={editingUser.id === currentUserId}
                onSave={handleUpdate}
                onCancel={() => setEditingUser(null)}
              />
            ) : (
              <CreateUserForm onSubmit={handleCreate} />
            )}
          </aside>
        )}
      </section>
    </div>
  )
}
