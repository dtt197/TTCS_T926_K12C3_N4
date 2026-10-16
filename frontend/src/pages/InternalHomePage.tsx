import { useEffect, useState } from 'react'

import { apiRequest } from '../services/apiClient'

import { hasPermission, type Permission } from '../permissions/rolePermissions'

import type { LoginResponse } from '../types/auth'

import { AuditLogPage } from './AuditLogPage'

import { ReminderEmailLogPage } from './ReminderEmailLogPage'

import { RoomManagementPage } from './RoomManagementPage'

import { AmenityPage } from './AmenityPage'

import { PriceOverridePage } from './PriceOverridePage'

import { SettingsPage } from './SettingsPage'

import { RoomStatusPage } from './RoomStatusPage'

import { RoomCalendarPage } from './RoomCalendarPage'

import { RoomTypePage } from './RoomTypePage'

import { UserManagementPage } from './UserManagementPage'

import { BookingListPage } from './BookingListPage'
import { WalkInBookingPage } from './WalkInBookingPage'
import { RevenueReportPage } from './RevenueReportPage'
import { OccupancyReportPage } from './OccupancyReportPage'
import { SecurityEndpointMatrixPage } from './SecurityEndpointMatrixPage'
import { SampleDataPage } from './SampleDataPage'

import { NotificationBell } from '../components/NotificationBell'

import { MaintenancePage } from './MaintenancePage'

import './AuthPages.css'
import './MaintenanceHeader.css'

type InternalHomePageProps = {

  user: LoginResponse

  onLogout: () => void

}

type View =

  | 'roomCalendar'

  | 'rooms'

  | 'roomManagement'

  | 'roomTypes'

  | 'amenities'

  | 'priceOverrides'

  | 'settings'

  | 'users'

  | 'audit-logs'

  | 'reminderEmails'

  | 'bookings'

  | 'walkInBooking'
  | 'revenueReport'
  | 'occupancyReport'

  | 'security-matrix'

  | 'sampleData'

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
    view: 'occupancyReport',
    path: '/bao-cao-cong-suat',
    label: 'Công suất phòng',
    title: 'Báo cáo công suất phòng',
    icon: '◔',
    permission: 'reports:occupancy',
  },
  {

    view: 'roomCalendar',

    path: '/room-calendar',

    label: 'Sơ đồ phòng theo ngày',

    title: 'Sơ đồ phòng theo ngày',

    icon: '▦',

    permission: 'rooms:calendar',

  },

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

    permission: 'bookings:manage',

  },

  {
    view: 'revenueReport',
    path: '/revenue-report',
    label: 'Doanh thu',
    title: 'Thống kê doanh thu',
    icon: '₫',
    permission: 'reports:revenue',
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

  {

    // S4-05: email nhắc nhận phòng (chỉ ADMIN, khớp /api/admin/** ở backend).

    view: 'reminderEmails',

    path: '/reminder-emails',
    label: 'Email đã gửi',
    title: 'Email đã gửi cho khách',
    icon: '✉',

    permission: 'auditLogs:view',

  },
  {
    view: 'security-matrix',
    path: '/security-endpoint-matrix',
    label: 'Ma trận quyền API',
    title: 'Đối chiếu ma trận quyền API',
    icon: '⚿',
    permission: 'security:matrix:view',
  },
  {
    // S4-09: Nạp bộ dữ liệu mẫu – chỉ Chủ homestay (OWNER) được phép.
    view: 'sampleData',
    path: '/sample-data',
    label: 'Dữ liệu mẫu',
    title: 'Nạp dữ liệu mẫu',
    icon: '📦',
    permission: 'sampleData:manage',
  },
]

const SESSION_CHECK_INTERVAL_MS = 30_000

function getViewFromPath(pathname: string): View | undefined {

  return TABS.find((tab) => tab.path === pathname)?.view

}

function getMaintenanceId(pathname: string): number | null | undefined {
  const match = pathname.match(/^\/maintenance(?:\/(\d+))?\/?$/)
  if (!match) return undefined
  if (!match[1]) return null
  const id = Number(match[1])
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}

export function InternalHomePage({

  user,

  onLogout,

}: InternalHomePageProps) {

  const canAccessMaintenance = user.role === 'OWNER' || user.role === 'RECEPTIONIST'
  const [maintenance, setMaintenance] = useState<{ id: number | null } | null>(() => {
    const id = getMaintenanceId(window.location.pathname)
    return canAccessMaintenance && id !== undefined ? { id } : null
  })
  const [maintenanceMenuOpen, setMaintenanceMenuOpen] = useState(false)

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

  const isMaintenanceRoute =
    canAccessMaintenance && getMaintenanceId(window.location.pathname) !== undefined
  const headerTitle = isMaintenanceRoute ? 'Bảo trì' : activeTab?.title ?? 'HomeStay'

  useEffect(() => {

    const currentPath = window.location.pathname
    const currentMaintenanceId = getMaintenanceId(currentPath)
    if (currentPath.startsWith('/maintenance') && (!canAccessMaintenance || currentMaintenanceId === undefined)) {
      window.history.replaceState({}, '', fallbackTab.path)
    }

    function handlePopState() {

      const maintenanceId = getMaintenanceId(window.location.pathname)
      if (maintenanceId !== undefined && canAccessMaintenance) {
        setMaintenance({ id: maintenanceId })
        setMaintenanceMenuOpen(false)
        return
      }
      setMaintenance(null)

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

  }, [user.role, fallbackTab, canAccessMaintenance])

  useEffect(() => {

    const timer = window.setInterval(() => {

      apiRequest('/api/internal/me').catch(() => {})

    }, SESSION_CHECK_INTERVAL_MS)

    return () => window.clearInterval(timer)

  }, [])

  function navigateTo(tab: Tab) {

    setMaintenance(null)

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

  function openMaintenance(id: number | null = null) {
    if (!canAccessMaintenance) return
    const pathname = id === null ? '/maintenance' : `/maintenance/${id}`
    if (window.location.pathname !== pathname) {
      const returnPath = getMaintenanceId(window.location.pathname) !== undefined
        ? window.history.state?.maintenanceReturnPath
        : window.location.pathname
      window.history.pushState({ maintenanceReturnPath: returnPath }, '', pathname)
    }
    setMaintenance({ id })
    setMaintenanceMenuOpen(false)
  }

  function closeMaintenance() {
    if (!canAccessMaintenance) return
    const returnPath = window.history.state?.maintenanceReturnPath
    const returnTab = typeof returnPath === 'string'
      ? TABS.find((tab) => tab.path === returnPath && hasPermission(user.role, tab.permission))
      : undefined
    const destination = returnTab?.path ?? fallbackTab.path
    window.history.replaceState({}, '', destination)
    setMaintenance(null)
    setView(returnTab?.view ?? fallbackTab.view)
    setMaintenanceMenuOpen(false)
  }

  return (

    <main className={`internal-layout${activeView === 'walkInBooking' ? ' internal-layout--walk-in-booking' : ''}`}>

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

                  !isMaintenanceRoute && activeView === tab.view

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

              {headerTitle}

            </h1>

            <p className="dashboard-subtitle">

              Quản lý hoạt động homestay của bạn

            </p>

          </div>

          <div className="dashboard-header-actions">

            {(user.role === 'OWNER' || user.role === 'RECEPTIONIST') && (

              <>

               <div className="maintenance-header-menu">
  <button
    type="button"
    className={`maintenance-header-trigger${isMaintenanceRoute ? ' active' : ''}`}
    aria-expanded={maintenanceMenuOpen}
    aria-haspopup="menu"
    onClick={() => setMaintenanceMenuOpen((current) => !current)}
  >
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M14.7 6.3a5 5 0 0 0-6.4 6.4L3 18a2 2 0 0 0 3 3l5.3-5.3a5 5 0 0 0 6.4-6.4L14 13l-3-3z" />
    </svg>
    <span>Bảo trì</span>

  </button>

  {maintenanceMenuOpen && (
    <div className="maintenance-header-dropdown">
      <button
        type="button"
        className="maintenance-header-option"
        onClick={() => {
          openMaintenance()
        }}
      >
        <span>
          <strong>Xem yêu cầu bảo trì</strong>
          <small>Danh sách và trạng thái xử lý</small>
        </span>
      </button>
    </div>
  )}
</div>

              </>

            )}

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

            {(user.role === 'OWNER' || user.role === 'RECEPTIONIST') && (
              <NotificationBell
                key={user.userId}
                userId={user.userId}
                onOpenMaintenance={(id) => openMaintenance(id)}
              />
            )}

          </div>

        </header>

        <div className="internal-page-content">

          {maintenance && canAccessMaintenance ? (

            <MaintenancePage key={user.userId} requestId={maintenance.id} owner={user.role === 'OWNER'}

              housekeeping={false} onSelect={id => openMaintenance(id)} onClose={closeMaintenance} />

          ) : activeView === 'roomCalendar' ? (

            <RoomCalendarPage />
          ) : activeView === 'occupancyReport' ? (
            <OccupancyReportPage />


          ) : activeView === 'bookings' ? (

            <BookingListPage role={user.role} />

              ) : activeView === 'walkInBooking' ? (

                <WalkInBookingPage />

              ) : activeView === 'revenueReport' ? (

                <RevenueReportPage />

              ) : activeView === 'audit-logs' ? (

            <AuditLogPage />

          ) : activeView === 'reminderEmails' ? (

            <ReminderEmailLogPage />
          ) : activeView === 'security-matrix' ? (
          <SecurityEndpointMatrixPage />
          ) : activeView === 'sampleData' ? (
            <SampleDataPage />
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

            <RoomStatusPage role={user.role} currentUser={user} />

          )}

        </div>

      </section>

    </main>

  )

}
