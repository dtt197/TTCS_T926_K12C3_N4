import type { LoginResponse } from '../types/auth'

type InternalHomePageProps = {
  user: LoginResponse
  onLogout: () => void
}

export function InternalHomePage({ user, onLogout }: InternalHomePageProps) {
  return (
    <main className="internal-layout">
      <header className="internal-header">
        <div className="internal-brand">HomeStay / Operations</div>
        <button className="logout-button" type="button" onClick={onLogout}>
          Đăng xuất
        </button>
      </header>
      <section className="internal-panel">
        <p className="welcome-label">Đăng nhập thành công</p>
        <h1>Xin chào, {user.fullName}</h1>
        <p>Đây là trang nội bộ dùng để demo luồng đăng nhập của Lát 1.</p>
        <span className="role-badge">Vai trò: {user.role}</span>
      </section>
    </main>
  )
}
