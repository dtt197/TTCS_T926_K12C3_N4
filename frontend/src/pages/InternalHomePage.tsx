import { useEffect, useState } from 'react'
import { apiRequest } from '../services/apiClient'
import type { LoginResponse } from '../types/auth'
import { RoomStatusPage } from './RoomStatusPage'
import { UserManagementPage } from './UserManagementPage'
import './AuthPages.css'

type InternalHomePageProps = {
  user: LoginResponse
  onLogout: () => void
}

type View = 'rooms' | 'users'

const SESSION_CHECK_INTERVAL_MS = 30_000

export function InternalHomePage({ user, onLogout }: InternalHomePageProps) {
  const [view, setView] = useState<View>('rooms')
  const isAdmin = user.role === 'ADMIN'

  // S1-02 AC5: cứ 30 giây kiểm tra phiên; tài khoản bị vô hiệu hoá thì apiClient tự đăng xuất
  useEffect(() => {
    const timer = window.setInterval(() => {
      apiRequest('/api/internal/me').catch(() => {})
    }, SESSION_CHECK_INTERVAL_MS)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <main className="internal-layout">
      <header className="internal-header">
        <div className="internal-brand">HomeStay / Operations</div>
        {isAdmin && (
          <nav className="view-tabs" aria-label="Chọn màn hình">
            <button type="button" className={view === 'rooms' ? 'primary-button' : 'secondary-button'}
              onClick={() => setView('rooms')}>Phòng</button>
            <button type="button" className={view === 'users' ? 'primary-button' : 'secondary-button'}
              onClick={() => setView('users')}>Tài khoản</button>
          </nav>
        )}
        <span className="role-badge">
          {user.fullName} · {user.role}
        </span>
        <button className="logout-button" type="button" onClick={onLogout}>
          Đăng xuất
        </button>
      </header>
      {isAdmin && view === 'users' ? <UserManagementPage currentUserId={user.userId} /> : <RoomStatusPage />}
    </main>
  )
}