import { useEffect, useState } from 'react'
import '../App.css'
import './UserManagementPage.css'
import { CreateUserForm } from '../components/CreateUserForm'
import { createUser, getUsers } from '../services/userService'
import { ROLE_LABELS, type CreateUserPayload, type StaffUser } from '../types/user'

/** S1-02: quản trị hệ thống tạo tài khoản nhân viên và xem danh sách. */
export function UserManagementPage() {
  const [users, setUsers] = useState<StaffUser[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

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
    setNotice(`Đã tạo tài khoản ${created.email}. Mật khẩu tạm đã được gửi qua email.`)
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
            {notice && <div className="alert success" role="status">{notice}</div>}
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
                          <span className="user-tag">{u.active ? 'Hoạt động' : 'Vô hiệu hoá'}</span>
                          {u.mustChangePassword && <span className="user-tag warn">Chưa đổi mật khẩu tạm</span>}
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