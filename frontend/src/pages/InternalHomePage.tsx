import { useState } from 'react'
import type { LoginResponse } from '../types/auth'
import { RoomStatusPage } from './RoomStatusPage'
import { UserManagementPage } from './UserManagementPage'
import './AuthPages.css'

type InternalHomePageProps = {
  user: LoginResponse
  onLogout: () => void
}

type View = 'rooms' | 'users'

export function InternalHomePage({ user, onLogout }: InternalHomePageProps) {
  const [view, setView] = useState<View>('rooms')
  const isAdmin = user.role === 'ADMIN'

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
      {isAdmin && view === 'users' ? <UserManagementPage /> : <RoomStatusPage />}
    </main>
  )
}