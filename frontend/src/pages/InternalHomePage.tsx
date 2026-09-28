
import { useEffect, useState } from 'react'
import { apiRequest } from '../services/apiClient'
import { hasPermission, type Permission } from '../permissions/rolePermissions'
import type { LoginResponse } from '../types/auth'
import { AuditLogPage } from './AuditLogPage'
import { RoomManagementPage } from './RoomManagementPage'
import { AmenityPage } from './AmenityPage'
import { RoomStatusPage } from './RoomStatusPage'
import { RoomTypePage } from './RoomTypePage'
import { UserManagementPage } from './UserManagementPage'
import './AuthPages.css'

type InternalHomePageProps = {
  user: LoginResponse
  onLogout: () => void
}

type View =
  | 'rooms'
  | 'roomManagement'
  | 'roomTypes'
  | 'amenities'
  | 'users'
  | 'audit-logs'

/** S1-04 + S1-05 + S1-06 + S1-07 + S1-08: menu theo quyền của vai trò. */
const TABS: ReadonlyArray<{
  view: View
  label: string
  permission: Permission
}> = [
  { view: 'rooms', label: 'Phòng', permission: 'rooms:view' },
  { view: 'roomManagement', label: 'Quản lý phòng', permission: 'rooms:manage' },
  { view: 'roomTypes', label: 'Loại phòng', permission: 'roomTypes:view' },
  { view: 'amenities', label: 'Tiện nghi', permission: 'amenities:view' },
  { view: 'users', label: 'Tài khoản', permission: 'accounts:view' },
  { view: 'audit-logs', label: 'Nhật ký', permission: 'auditLogs:view' },
]

const SESSION_CHECK_INTERVAL_MS = 30_000
const VIEW_STORAGE_KEY = 'homestay_internal_view'

function getInitialView(): View {
  const savedView = window.sessionStorage.getItem(VIEW_STORAGE_KEY)
  return TABS.find((tab) => tab.view === savedView)?.view ?? 'rooms'
}

export function InternalHomePage({
  user,
  onLogout,
}: InternalHomePageProps) {
  const [view, setView] = useState<View>(getInitialView)

  const visibleTabs = TABS.filter((tab) =>
    hasPermission(user.role, tab.permission)
  )

  // Tab không còn quyền thì quay về trang Phòng.
  const activeView: View = visibleTabs.some(
    (tab) => tab.view === view
  )
    ? view
    : 'rooms'

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

        {visibleTabs.length > 1 && (
          <nav className="view-tabs" aria-label="Chọn màn hình">
            {visibleTabs.map((tab) => (
              <button
                key={tab.view}
                type="button"
                className={
                  activeView === tab.view
                    ? 'primary-button'
                    : 'secondary-button'
                }
                onClick={() => setView(tab.view)}
              >
                {tab.label}
              </button>
            ))}
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

      {activeView === 'audit-logs' ? (
        <AuditLogPage />
      ) : activeView === 'roomManagement' ? (
        <RoomManagementPage />
      ) : activeView === 'users' ? (
        <UserManagementPage
          currentUserId={user.userId}
          role={user.role}
        />
      ) : activeView === 'roomTypes' ? (
        <RoomTypePage role={user.role} />
      ) : activeView === 'amenities' ? (
        <AmenityPage role={user.role} />
      ) : (
        <RoomStatusPage role={user.role} />
      )}
    </main>
  )
}
