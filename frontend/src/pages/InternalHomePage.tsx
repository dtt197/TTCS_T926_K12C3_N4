import { useEffect, useState } from 'react'
import { apiRequest } from '../services/apiClient'
import { hasPermission, type Permission } from '../permissions/rolePermissions'
import type { LoginResponse } from '../types/auth'
import { AuditLogPage } from './AuditLogPage'
import { RoomManagementPage } from './RoomManagementPage'
import { AmenityPage } from './AmenityPage'
import { PriceOverridePage } from './PriceOverridePage'
import { SettingsPage } from './SettingsPage'
import { RoomStatusPage } from './RoomStatusPage'
import { RoomTypePage } from './RoomTypePage'
import { UserManagementPage } from './UserManagementPage'
import { BookingListPage } from './BookingListPage'
import { WalkInBookingPage } from './WalkInBookingPage'
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
  | 'priceOverrides'
  | 'settings'
  | 'users'
  | 'audit-logs'
  | 'bookings'
  | 'walkInBooking'

type Tab = {
  view: View
  path: string
  label: string
  title: string
  icon: string
  permission: Permission
}

const TABS: ReadonlyArray<Tab> = [
    {
    // S1-04 / S1-10: trạng thái phòng, nhận - trả phòng, dọn phòng, bảo trì (Lễ tân, Buồng phòng...).
    view: 'rooms',
    path: '/rooms',
    label: 'Phòng',
    title: 'Phòng',
    icon: '▣',
    permission: 'rooms:view',
  },
  {
    view: 'bookings',
    path: '/bookings',
    label: 'Booking',
    title: 'Danh sách booking mới',
    icon: '▤',
    permission: 'bookings:view',
  },
  {
    view: 'walkInBooking',
    path: '/booking-tai-quay',
    label: 'Booking tại quầy',
    title: 'Booking tại quầy',
    icon: '+',
    permission: 'bookings:view',
  },
  {
    view: 'roomManagement',
    path: '/room-management',
    label: 'Quản lý phòng',
    title: 'Quản lý phòng',
    icon: '▤',
    permission: 'rooms:manage',
  },
  {
    view: 'roomTypes',
    path: '/room-types',
    label: 'Loại phòng',
    title: 'Loại phòng',
    icon: '▦',
    permission: 'roomTypes:view',
  },
  {
    view: 'amenities',
    path: '/amenities',
    label: 'Tiện nghi',
    title: 'Tiện nghi',
    icon: '✦',
    permission: 'amenities:view',
  },
  {
    view: 'priceOverrides',
    path: '/price-overrides',
    label: 'Giá đè',
    title: 'Giá đè theo mùa / ngày lễ',
    icon: '₫',
    permission: 'pricing:view',
  },
  {
    view: 'settings',
    path: '/settings',
    label: 'Tham số ',
    title: 'Tham số',
    icon: '⚙',
    permission: 'settings:view',
  },
  {
    view: 'users',
    path: '/users',
    label: 'Tài khoản',
    title: 'Tài khoản nhân viên',
    icon: '●',
    permission: 'accounts:view',
  },
  {
    view: 'audit-logs',
    path: '/audit-logs',
    label: 'Nhật ký',
    title: 'Nhật ký hoạt động',
    icon: '☷',
    permission: 'auditLogs:view',
  },
]

const SESSION_CHECK_INTERVAL_MS = 30_000

function getViewFromPath(pathname: string): View | undefined {
  return TABS.find((tab) => tab.path === pathname)?.view
}

export function InternalHomePage({
  user,
  onLogout,
}: InternalHomePageProps) {
  const [view, setView] = useState<View>(() => {
    const viewFromPath = getViewFromPath(window.location.pathname)

    if (viewFromPath) {
      return viewFromPath
    }

    return user.role === 'RECEPTIONIST' ? 'bookings' : 'rooms'
  })

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
        if (nextView) setView(nextView)
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
              {activeTab?.title ?? 'HomeStay'}
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
          {activeView === 'bookings' ? (
            <BookingListPage />
          ) : activeView === 'walkInBooking' ? (
            <WalkInBookingPage />
          ) : activeView === 'audit-logs' ? (
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
          ) : activeView === 'priceOverrides' ? (
            <PriceOverridePage role={user.role} />
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