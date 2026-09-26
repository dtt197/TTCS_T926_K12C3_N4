import { useEffect, useState } from 'react'
import '../App.css'
import './UserManagementPage.css'
import { CreateUserForm } from '../components/CreateUserForm'
import { createUser, getUsers, updateUserStatus } from '../services/userService'
import { ROLE_LABELS, type CreateUserPayload, type StaffUser } from '../types/user'

type UserManagementPageProps = {
  currentUserId: number
}

type Notice = {
  type: 'success' | 'error'
  text: string
}

/** S1-02: quản trị hệ thống tạo, xem, vô hiệu hoá tài khoản nhân viên. */
export function UserManagementPage({ currentUserId }: UserManagementPageProps) {
  const [users, setUsers] = useState<StaffUser[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [busyUserId, setBusyUserId] = useState<number | null>(null)

  useEffect(() => {
    getUsers()
      .then(setUsers)
      .catch((err: unknown) =>
        setLoadError(err instanceof Error ? err.message : 'Không tải được danh sách tài khoản'))
      .finally(() => setIsLoading(false))
  }, [])

  async function handleCreate(payload: CreateUserPayload) {
    const created = await createUser(payload)
    setUsers((current) => [created, ...current])
    setNotice({ type: 'success', text: `Đã tạo tài khoản ${created.email}. Mật khẩu tạm đã được gửi qua email.` })
  }

  /** AC5: vô hiệu hoá làm phiên đăng nhập hiện tại của người đó hết hiệu lực. */
  async function handleToggleActive(target: StaffUser) {
    const nextActive = !target.active
    const question = nextActive
      ? `Kích hoạt lại tài khoản ${target.email}?`
      : `Vô hiệu hoá tài khoản ${target.email}? Người này sẽ bị đăng xuất và không đăng nhập được nữa.`
    if (!window.confirm(question)) {
      return
    }
    setBusyUserId(target.id)
    setNotice(null)
    try {
      const updated = await updateUserStatus(target.id, nextActive)
      setUsers((current) => current.map((u) => (u.id === updated.id ? updated : u)))
      setNotice({
        type: 'success',
        text: nextActive
          ? `Đã kích hoạt lại tài khoản ${updated.email}.`
          : `Đã vô hiệu hoá ${updated.email}. Phiên đăng nhập của người này đã hết hiệu lực.`,
      })
    } catch (err) {
      setNotice({ type: 'error', text: err instanceof Error ? err.message : 'Không cập nhật được trạng thái' })
    } finally {
      setBusyUserId(null)
    }
  }

  return (
    <div className="app-shell">
      <main className="main-content">
        <section className="intro">
          <div>
            <p className="eyebrow">Quản trị · S1-02</p>
            <h1>Tài khoản nhân viên</h1>
            <p>Mỗi nhân viên một tài khoản riêng, gán đúng một vai trò.</p>
          </div>
        </section>

        <section className="workspace-grid">
          <div className="panel">
            <div className="panel-heading">
              <div>
                <h2>Danh sách tài khoản</h2>
                <p>{users.length} tài khoản</p>
              </div>
            </div>
            {notice && (
              <div className={`alert ${notice.type === 'success' ? 'success' : ''}`} role="status">
                {notice.text}
              </div>
            )}
            {isLoading ? (
              <p className="empty-state">Đang tải...</p>
            ) : loadError ? (
              <div className="alert" role="alert">{loadError}</div>
            ) : users.length === 0 ? (
              <p className="empty-state">Chưa có tài khoản nào.</p>
            ) : (
              <div className="user-table-wrapper">
                <table className="user-table">
                  <thead>
                    <tr>
                      <th>Họ tên</th>
                      <th>Vai trò</th>
                      <th>Điện thoại</th>
                      <th>Trạng thái</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {users.map((u) => (
                      <tr key={u.id}>
                        <td>
                          {u.fullName}
                          <small>{u.email}</small>
                        </td>
                        <td>{ROLE_LABELS[u.role] ?? u.role}</td>
                        <td>{u.phone ?? '—'}</td>
                        <td>
                          <span className={`user-tag ${u.active ? '' : 'off'}`}>
                            {u.active ? 'Hoạt động' : 'Vô hiệu hoá'}
                          </span>
                          {u.mustChangePassword && <span className="user-tag warn">Chưa đổi mật khẩu tạm</span>}
                        </td>
                        <td>
                          {u.id === currentUserId ? (
                            <small>Tài khoản của bạn</small>
                          ) : (
                            <button type="button" className="secondary-button"
                              disabled={busyUserId === u.id} onClick={() => handleToggleActive(u)}>
                              {u.active ? 'Vô hiệu hoá' : 'Kích hoạt lại'}
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <aside className="panel">
            <div className="panel-heading">
              <div>
                <h2>Tạo tài khoản</h2>
                <p>Chỉ Quản trị hệ thống thực hiện được.</p>
              </div>
            </div>
            <CreateUserForm onSubmit={handleCreate} />
          </aside>
        </section>
      </main>
    </div>
  )
}