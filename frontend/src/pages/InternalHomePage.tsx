import type { LoginResponse } from '../types/auth'
import { RoomStatusPage } from './RoomStatusPage'
import './AuthPages.css'

type InternalHomePageProps = {
  user: LoginResponse
  onLogout: () => void
}

export function InternalHomePage({ user, onLogout }: InternalHomePageProps) {
  return (
    <main className="internal-layout">
      <header className="internal-header">
        <div className="internal-brand">HomeStay / Operations</div>
        <span className="role-badge">
          {user.fullName} · {user.role}
        </span>
        <button className="logout-button" type="button" onClick={onLogout}>
          Đăng xuất
        </button>
      </header>
      <RoomStatusPage />
    </main>
  )
}