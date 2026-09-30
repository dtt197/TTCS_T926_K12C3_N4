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

type Tab = {
  view: View
  path: string
  label: string
  icon: string
  permission: Permission
}

const TABS: ReadonlyArray<Tab> = [
  {
    view: 'rooms',
    path: '/rooms',
    label: 'Phòng',
    icon: '▣',
    permission: 'rooms:view',
  },
  {
    view: 'roomManagement',
    path: '/room-management',
    label: 'Quản lý phòng',
    icon: '▤',
    permission: 'rooms:manage',
  },
  {
    view: 'roomTypes',
    path: '/room-types',
    label: 'Loại phòng',
    icon: '▦',
    permission: 'roomTypes:view',
  },
  {
    view: 'amenities',
    path: '/amenities',
    label: 'Tiện nghi',
    icon: '✦',
    permission: 'amenities:view',
  },
  {
    view: 'settings',
    path: '/settings',
    label: 'Tham số',
    icon: '⚙',
    permission: 'settings:view',
  },
  {
    view: 'users',
    path: '/users',
    label: 'Tài khoản',
    icon: '●',
    permission: 'accounts:view',
  },
  {
    view: 'audit-logs',
    path: '/audit-logs',
    label: 'Nhật ký',
    icon: '☷',
    permission: 'auditLogs:view',
  },
]

const SESSION_CHECK_INTERVAL_MS = 30_000

function getViewFromPath(pathname: string): View {
  return TABS.find((tab) => tab.path === pathname)?.view ?? 'rooms'
}

export function InternalHomePage({
  user,
  onLogout,
}: InternalHomePageProps) {
  const [view, setView] = useState<View>(() =>
    getViewFromPath(window.location.pathname),
  )

  const visibleTabs = TABS.filter((tab) =>
    hasPermission(user.role, tab.permission),
  )

  const fallbackTab =
    visibleTabs[0] ??
    TABS[0]

  const activeView: View = visibleTabs.some(
    (tab) => tab.view === view,
  )
    ? view
    : fallbackTab.view

  const activeTab =
    visibleTabs.find((tab) => tab.view === activeView) ??
    fallbackTab

  useEffect(() => {
    const currentTab = TABS.find(
      (tab) => tab.path === window.location.pathname,
    )

    const currentTabAllowed =
      currentTab &&
      hasPermission(user.role, currentTab.permission)

    if (!currentTabAllowed) {
      window.history.replaceState(
        {},
        '',
        fallbackTab.path,
      )

      setView(fallbackTab.view)
    }
  }, [user.role, fallbackTab])

  useEffect(() => {
    function handlePopState() {
      const nextView = getViewFromPath(
        window.location.pathname,
      )

      const nextTab = TABS.find(
        (tab) => tab.view === nextView,
      )

      if (
        nextTab &&
        hasPermission(user.role, nextTab.permission)
      ) {
        setView(nextView)
        return
      }

      window.history.replaceState(
        {},
        '',
        fallbackTab.path,
      )

      setView(fallbackTab.view)
    }

    window.addEventListener(
      'popstate',
      handlePopState,
    )

    return () => {
      window.removeEventListener(
        'popstate',
        handlePopState,
      )
    }
  }, [user.role, fallbackTab])

  useEffect(() => {
    const timer = window.setInterval(() => {
      apiRequest('/api/internal/me').catch(() => {})
    }, SESSION_CHECK_INTERVAL_MS)

    return () => window.clearInterval(timer)
  }, [])

  function navigateTo(tab: Tab) {
    if (!hasPermission(user.role, tab.permission)) {
      return
    }

    if (window.location.pathname !== tab.path) {
      window.history.pushState(
        {},
        '',
        tab.path,
      )
    }

    setView(tab.view)
  }

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
                onClick={() => navigateTo(tab)}
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

            <h1>
              {activeTab?.label ?? 'HomeStay'}
            </h1>

            <p className="dashboard-subtitle">
              Quản lý hoạt động homestay của bạn
            </p>
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