import { useEffect, useState } from 'react'
import { BookingLookupPage } from '../pages/BookingLookupPage'
import { ChangePasswordPage } from '../pages/ChangePasswordPage'
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage'
import { GuestBookingPage } from '../pages/GuestBookingPage'
import { HomePage } from '../pages/HomePage'
import { InternalHomePage } from '../pages/InternalHomePage'
import { LoginPage } from '../pages/LoginPage'
import { PublicRoomSearchPage } from '../pages/PublicRoomSearchPage'
import { ResetPasswordPage } from '../pages/ResetPasswordPage'
import { RoomDetailPage } from '../pages/RoomDetailPage'
import { RoomTypeListPage } from '../pages/RoomTypeListPage'
import type { LoginResponse } from '../types/auth'

type AppRoutesProps = {
  user: LoginResponse | null
  onLogin: (user: LoginResponse) => void
  onLogout: () => void
  onPasswordChanged: () => void
}
/** Đường dẫn cũ → đường dẫn mới; null nếu không cần đổi. */
function legacyRedirectTarget(pathname: string): string | null {
  if (pathname === '/') return '/trang-chu'
  if (pathname === '/loai-phong') return '/danh-sach-loai-phong'
  if (pathname.startsWith('/phong/')) return pathname.replace('/phong/', '/loai-phong/')
  return null
}

export function AppRoutes({
  user,
  onLogin,
  onLogout,
  onPasswordChanged,
}: AppRoutesProps) {
  const [, setRouteVersion] = useState(0)

  useEffect(() => {
    const handlePopState = () => setRouteVersion((version) => version + 1)
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const currentPath = window.location.pathname
  const redirectTarget = legacyRedirectTarget(currentPath)
  // Vẽ ngay theo đường dẫn mới. Trước đây chỉ đổi thanh địa chỉ nên form đăng nhập vẫn hiện dưới /trang-chu,
  // đăng nhập xong bị đưa sang trang chủ của khách.
  const pathname = redirectTarget ?? currentPath

  useEffect(() => {
    if (redirectTarget) {
      window.history.replaceState(null, '', redirectTarget)
    }
  }, [redirectTarget])

  let page

  if (pathname === '/trang-chu') {
    page = <HomePage />
  } else if (pathname === '/forgot-password') {
    page = <ForgotPasswordPage />
  } else if (pathname === '/reset-password') {
    page = <ResetPasswordPage />
  } else if (pathname === '/tim-phong') {
    page = <PublicRoomSearchPage />
  } else if (pathname === '/dat-phong') {
    // S2-07: trang đặt phòng công khai, khách không cần đăng nhập
    page = <GuestBookingPage />
  } else if (pathname === '/tra-cuu-booking' || pathname === '/tra-cuu-dat-phong') {
    // S2-08: khách tra cứu booking bằng mã và email, không cần đăng nhập
    page = <BookingLookupPage />

  } else if (pathname.startsWith('/loai-phong/')) {
    // S2-04: trang chi tiết loại phòng (bộ ảnh, tiện nghi, giờ nhận/trả, phụ thu, chính sách huỷ)
    const roomTypeId = Number(pathname.slice('/loai-phong/'.length))
    page = <RoomDetailPage roomTypeId={Number.isSafeInteger(roomTypeId) && roomTypeId > 0 ? roomTypeId : null} />
  } else if (pathname === '/danh-sach-loai-phong') {
    // S2-03: trang danh sách loại phòng công khai, khách không cần đăng nhập
    page = <RoomTypeListPage />
    // Nhân viên đăng nhập qua /dang-nhap (hoặc bất kỳ đường dẫn nội bộ nào); đăng nhập xong vào trang quản lý.
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