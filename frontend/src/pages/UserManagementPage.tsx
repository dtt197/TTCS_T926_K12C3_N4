
import { useEffect, useState } from 'react'
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
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [busyUserId, setBusyUserId] = useState<number | null>(null)
  const [editingUser, setEditingUser] = useState<StaffUser | null>(null)
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)

  useEffect(() => {
    if (!canViewAccounts) {
      setIsLoading(false)
      return
    }

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
      <div className="app-shell">
        <main className="main-content">
          <div className="alert" role="alert">
            Bạn không có quyền xem danh sách tài khoản.
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className="app-shell">
      <main className="main-content">
        <section className="intro">
          <div>
            <p className="eyebrow">
              {canManageAccounts
                ? 'Quản trị · S1-02'
                : 'Phân quyền · S1-04'}
            </p>

            <h1>Tài khoản nhân viên</h1>

            <p>
              {canManageAccounts
                ? 'Mỗi nhân viên một tài khoản riêng, gán đúng một vai trò.'
                : 'Xem danh sách và trạng thái tài khoản nhân viên.'}
            </p>
          </div>
        </section>

        <section className="workspace-grid">
          <div className="panel">
            <div className="panel-heading">
              <div>
                <h2>Danh sách tài khoản</h2>
                <p>
                  {filteredUsers.length} / {users.length} tài khoản
                </p>
              </div>
            </div>

            <input
              className="form-control user-search"
              placeholder="Tìm theo họ tên hoặc email..."
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value)
                setPage(0)
              }}
            />

            {notice && (
              <div
                className={`alert ${
                  notice.type === 'success' ? 'success' : ''
                }`}
                role="status"
              >
                {notice.text}
              </div>
            )}

            {isLoading ? (
              <p className="empty-state">Đang tải...</p>
            ) : loadError ? (
              <div className="alert" role="alert">
                {loadError}
              </div>
            ) : visibleUsers.length === 0 ? (
              <p className="empty-state">
                {users.length === 0
                  ? 'Chưa có tài khoản nào.'
                  : 'Không có tài khoản nào khớp từ khoá.'}
              </p>
            ) : (
              <div className="user-table-wrapper">
                <table className="user-table">
                  <thead>
                    <tr>
                      <th>Họ tên</th>
                      <th>Vai trò</th>
                      <th>Điện thoại</th>
                      <th>Trạng thái</th>

                      {canManageAccounts && <th>Thao tác</th>}
                    </tr>
                  </thead>

                  <tbody>
                    {visibleUsers.map((u) => {
                      const isSelf = u.id === currentUserId

                      return (
                        <tr key={u.id}>
                          <td>
                            {u.fullName}
                            <small>
                              {u.email}
                              {isSelf ? ' · bạn' : ''}
                            </small>
                          </td>

                          <td>
                            {ROLE_LABELS[u.role] ?? u.role}
                          </td>

                          <td>{u.phone ?? '—'}</td>

                          <td>
                            <span
                              className={`user-tag ${
                                u.active ? '' : 'off'
                              }`}
                            >
                              {u.active
                                ? 'Hoạt động'
                                : 'Vô hiệu hoá'}
                            </span>

                            {u.mustChangePassword && (
                              <span className="user-tag warn">
                                Chưa đổi mật khẩu tạm
                              </span>
                            )}
                          </td>

                          {canManageAccounts && (
                            <td>
                              <div className="user-actions">
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
                                    className="secondary-button"
                                    disabled={busyUserId === u.id}
                                    onClick={() =>
                                      handleToggleActive(u)
                                    }
                                  >
                                    {u.active
                                      ? 'Vô hiệu hoá'
                                      : 'Kích hoạt lại'}
                                  </button>
                                )}
                              </div>
                            </td>
                          )}
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
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
            <aside className="panel">
              <div className="panel-heading">
                <div>
                  <h2>
                    {editingUser
                      ? 'Sửa tài khoản'
                      : 'Tạo tài khoản'}
                  </h2>

                  <p>
                    Chỉ Quản trị hệ thống thực hiện được.
                  </p>
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
      </main>
    </div>
  )
}
