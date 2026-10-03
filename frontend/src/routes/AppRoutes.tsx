import { useEffect, useState } from 'react'
import { ChangePasswordPage } from '../pages/ChangePasswordPage'
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage'
import { GuestBookingPage } from '../pages/GuestBookingPage'
import { InternalHomePage } from '../pages/InternalHomePage'
import { LoginPage } from '../pages/LoginPage'
import { PublicRoomListPage } from '../pages/PublicRoomListPage'
import { ResetPasswordPage } from '../pages/ResetPasswordPage'
import { RoomDetailPage } from '../pages/RoomDetailPage'
import type { LoginResponse } from '../types/auth'

type AppRoutesProps = {
  user: LoginResponse | null
  onLogin: (user: LoginResponse) => void
  onLogout: () => void
  onPasswordChanged: () => void
}

function parseLocationState() {
  const pathname = window.location.pathname
  const searchParams = new URLSearchParams(window.location.search)

  if (pathname.startsWith('/phong/') || pathname === '/chi-tiet-phong') {
    const parts = pathname.split('/').filter(Boolean)
    const last = parts[parts.length - 1]
    const id = /^\d+$/.test(last) ? last : searchParams.get('id')
    return { path: 'detail', id }
  }
  if (pathname === '/phong' && searchParams.get('id')) {
    return { path: 'detail', id: searchParams.get('id') }
  }
  if (pathname === '/phong' || pathname === '/danh-sach-phong') {
    return { path: 'list', id: null }
  }
  return { path: 'default', id: null }
}

export function AppRoutes({
  user,
  onLogin,
  onLogout,
  onPasswordChanged,
}: AppRoutesProps) {
  const [routeState, setRouteState] = useState(parseLocationState)

  useEffect(() => {
    const handlePopState = () => {
      setRouteState(parseLocationState())
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const pathname = window.location.pathname

  let page

  // S2-04: Trang chi tiết loại phòng (Room Detail Page)
  if (routeState.path === 'detail') {
    page = (
      <RoomDetailPage
        roomTypeId={routeState.id}
        onBack={() => {
          window.history.pushState({}, '', '/phong')
          setRouteState({ path: 'list', id: null })
        }}
        onBookNow={(id) => {
          window.location.href = `/dat-phong?roomTypeId=${id}`
        }}
      />
    )
  }
  // S2-04: Trang danh sách loại phòng công khai (Public Room List Page)
  else if (
    routeState.path === 'list' ||
    pathname === '/phong' ||
    pathname === '/danh-sach-phong'
  ) {
    page = (
      <PublicRoomListPage
        onSelectRoomType={(id) => {
          window.history.pushState({}, '', `/phong/${id}`)
          setRouteState({ path: 'detail', id: String(id) })
        }}
      />
    )
  } else if (pathname === '/forgot-password') {
    page = <ForgotPasswordPage />
  } else if (pathname === '/reset-password') {
    page = <ResetPasswordPage />
  } else if (pathname === '/dat-phong') {
    // S2-07: trang đặt phòng công khai, khách không cần đăng nhập
    page = <GuestBookingPage />
  } else if (!user) {
    page = <LoginPage onLogin={onLogin} />
  } else if (user.mustChangePassword) {
    // S1-02 AC4: còn mật khẩu tạm thì chỉ thấy màn hình đổi mật khẩu
    page = (
      <ChangePasswordPage
        user={user}
        onChanged={onPasswordChanged}
        onLogout={onLogout}
      />
    )
  } else {
    page = <InternalHomePage user={user} onLogout={onLogout} />
  }

  return <div className="app-shell">{page}</div>
}
