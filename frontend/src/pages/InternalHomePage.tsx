import { useEffect, useState } from 'react'
import { apiRequest } from '../services/apiClient'
import { hasPermission, type Permission } from '../permissions/rolePermissions'
import type { LoginResponse } from '../types/auth'
import { AuditLogPage } from './AuditLogPage'
import { RoomManagementPage } from './RoomManagementPage'
import { AmenityPage } from './AmenityPage'
import { SettingsPage } from './SettingsPage'
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
  | 'settings'
  | 'users'
  | 'audit-logs'

const TABS: ReadonlyArray<{
  view: View
  label: string
  icon: string
  permission: Permission
}> = [
  {
    view: 'rooms',
    label: 'Dashboard',
    icon: '◕',
    permission: 'rooms:view',
  },
  {
    view: 'roomManagement',
    label: 'Quản lý phòng',
    icon: '▤',
    permission: 'rooms:manage',
  },
  {
    view: 'roomTypes',
    label: 'Loại phòng',
    icon: '▦',
    permission: 'roomTypes:view',
  },
  {
    view: 'amenities',
    label: 'Tiện nghi',
    icon: '✦',
    permission: 'amenities:view',
  },
  {
    view: 'settings',
    label: 'Tham số',
    icon: '⚙',
    permission: 'settings:view',
  },
  {
    view: 'users',
    label: 'Tài khoản',
    icon: '●',
    permission: 'accounts:view',
  },
  {
    view: 'audit-logs',
    label: 'Nhật ký',
    icon: '☷',
    permission: 'auditLogs:view',
  },
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
    hasPermission(user.role, tab.permission),
  )

  const activeView: View = visibleTabs.some(
    (tab) => tab.view === view,
  )
    ? view
    : 'rooms'

  const activeTab =
    visibleTabs.find((tab) => tab.view === activeView) ??
    visibleTabs[0]

  useEffect(() => {
    window.sessionStorage.setItem(
      VIEW_STORAGE_KEY,
      activeView,
    )
  }, [activeView])

  useEffect(() => {
    const timer = window.setInterval(() => {
      apiRequest('/api/internal/me').catch(() => {})
    }, SESSION_CHECK_INTERVAL_MS)

    return () => window.clearInterval(timer)
  }, [])

  return (
    <main className="internal-layout">
      <aside className="app-sidebar">
        <div className="sidebar-brand">
          <div className="sidebar-logo">
            <span className="sidebar-house">⌂</span>
          </div>

          <div>
            <strong>HomeStay</strong>
            <span>Manager</span>
          </div>
        </div>

        <div className="sidebar-divider" />

        {visibleTabs.length > 0 && (
          <nav
            className="sidebar-navigation"
            aria-label="Chọn màn hình"
          >
            {visibleTabs.map((tab) => (
              <button
                key={tab.view}
                type="button"
                className={
                  activeView === tab.view
                    ? 'sidebar-nav-item active'
                    : 'sidebar-nav-item'
                }
                onClick={() => setView(tab.view)}
              >
                <span className="sidebar-nav-icon">
                  {tab.icon}
                </span>

                <span>{tab.label}</span>
              </button>
            ))}
          </nav>
        )}

        <div className="sidebar-spacer" />

        <div className="sidebar-welcome">
          <span className="sidebar-welcome-label">
            Xin chào
          </span>

          <strong>{user.fullName}</strong>

          <small>
            Quản lý homestay dễ dàng và hiệu quả hơn.
          </small>
        </div>

        <button
          className="sidebar-logout"
          type="button"
          onClick={onLogout}
        >
          <span>↪</span>
          Đăng xuất
        </button>
      </aside>

      <section className="internal-main">
        <header className="dashboard-header">
          <div>
            <p className="dashboard-eyebrow">
              HOMESTAY MANAGER
            </p>

            {activeView === 'rooms' ? (
              <>
                <h1>Dashboard</h1>

                <p className="dashboard-subtitle">
                  Tổng quan hoạt động homestay của bạn
                </p>
              </>
            ) : (
              <>
                <h1>{activeTab?.label ?? 'HomeStay'}</h1>

                <p className="dashboard-subtitle">
                  Quản lý hoạt động homestay của bạn
                </p>
              </>
            )}
          </div>

          <div className="dashboard-account">
            <div className="dashboard-avatar">
              {user.fullName
                ?.trim()
                .charAt(0)
                .toUpperCase()}
            </div>

            <div className="dashboard-user-info">
              <strong>{user.fullName}</strong>
              <span>{user.role}</span>
            </div>
          </div>
        </header>

        <div className="internal-page-content">
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
          ) : activeView === 'settings' ? (
            <SettingsPage role={user.role} />
          ) : (
            <RoomStatusPage role={user.role} />
          )}
        </div>
      </section>
    </main>
  )
}