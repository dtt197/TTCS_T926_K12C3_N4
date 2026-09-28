
import { useEffect, useState } from 'react'
import { apiRequest } from '../services/apiClient'
import { hasPermission } from '../permissions/rolePermissions'
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
const VIEW_STORAGE_KEY = 'homestay_internal_view'

function getInitialView(): View {
  const savedView = window.sessionStorage.getItem(VIEW_STORAGE_KEY)
  return savedView === 'users' ? 'users' : 'rooms'
}

export function InternalHomePage({
  user,
  onLogout,
}: InternalHomePageProps) {
  const [view, setView] = useState<View>(getInitialView)

  // S1-04: Kiểm tra quyền từ ma trận phân quyền tập trung.
  const canViewAccounts = hasPermission(user.role, 'accounts:view')

  // Người không có quyền xem tài khoản sẽ được chuyển về trang Phòng.
  const activeView: View =
    view === 'users' && !canViewAccounts ? 'rooms' : view

  // Lưu tab đang mở khi tải lại trang.
  useEffect(() => {
    window.sessionStorage.setItem(VIEW_STORAGE_KEY, activeView)
  }, [activeView])

  // S1-02 AC5: Kiểm tra phiên đăng nhập mỗi 30 giây.
  useEffect(() => {
    const timer = window.setInterval(() => {
      apiRequest('/api/internal/me').catch(() => {})
    }, SESSION_CHECK_INTERVAL_MS)

    return () => window.clearInterval(timer)
  }, [])

  return (
    <main className="internal-layout">
      <header className="internal-header">
        <div className="internal-brand">
          HomeStay / Operations
        </div>

        {canViewAccounts && (
          <nav className="view-tabs" aria-label="Chọn màn hình">
            <button
              type="button"
              className={
                activeView === 'rooms'
                  ? 'primary-button'
                  : 'secondary-button'
              }
              onClick={() => setView('rooms')}
            >
              Phòng
            </button>

            <button
              type="button"
              className={
                activeView === 'users'
                  ? 'primary-button'
                  : 'secondary-button'
              }
              onClick={() => setView('users')}
            >
              Tài khoản
            </button>
          </nav>
        )}

        <span className="role-badge">
          {user.fullName} · {user.role}
        </span>

        <button
          className="logout-button"
          type="button"
          onClick={onLogout}
        >
          Đăng xuất
        </button>
      </header>

      {canViewAccounts && activeView === 'users' ? (
        <UserManagementPage
          currentUserId={user.userId}
          role={user.role}
        />
      ) : (
        <RoomStatusPage role={user.role} />
      )}
    </main>
  )
}
