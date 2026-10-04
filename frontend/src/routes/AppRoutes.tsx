import { ChangePasswordPage } from '../pages/ChangePasswordPage'
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage'
import { GuestBookingPage } from '../pages/GuestBookingPage'
import { InternalHomePage } from '../pages/InternalHomePage'
import { LoginPage } from '../pages/LoginPage'
import { PublicRoomSearchPage } from '../pages/PublicRoomSearchPage'
import { ResetPasswordPage } from '../pages/ResetPasswordPage'
import { RoomTypeDetailsPage } from '../pages/RoomTypeDetailsPage'
import type { LoginResponse } from '../types/auth'

type AppRoutesProps = {
  user: LoginResponse | null
  onLogin: (user: LoginResponse) => void
  onLogout: () => void
  onPasswordChanged: () => void
}

export function AppRoutes({
  user,
  onLogin,
  onLogout,
  onPasswordChanged,
}: AppRoutesProps) {
  const pathname = window.location.pathname

  let page

  if (pathname === '/forgot-password') {
    page = <ForgotPasswordPage />
  } else if (pathname === '/reset-password') {
    page = <ResetPasswordPage />
  } else if (pathname === '/tim-phong') {
    page = <PublicRoomSearchPage />
  } else if (pathname === '/dat-phong') {
    // S2-07: trang đặt phòng công khai, khách không cần đăng nhập
    page = <GuestBookingPage />
  } else if (pathname === '/loai-phong' || pathname.startsWith('/loai-phong/')) {
    const roomTypeId = Number(pathname.slice('/loai-phong/'.length))
    page = <RoomTypeDetailsPage roomTypeId={Number.isSafeInteger(roomTypeId) && roomTypeId > 0 ? roomTypeId : null} />
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