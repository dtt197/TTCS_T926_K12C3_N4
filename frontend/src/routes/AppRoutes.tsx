import { ChangePasswordPage } from '../pages/ChangePasswordPage'
import { InternalHomePage } from '../pages/InternalHomePage'
import { LoginPage } from '../pages/LoginPage'
import type { LoginResponse } from '../types/auth'

type AppRoutesProps = {
  user: LoginResponse | null
  onLogin: (user: LoginResponse) => void
  onLogout: () => void
  onPasswordChanged: () => void
}

export function AppRoutes({ user, onLogin, onLogout, onPasswordChanged }: AppRoutesProps) {
  let page
  if (!user) {
    page = <LoginPage onLogin={onLogin} />
  } else if (user.mustChangePassword) {
    // S1-02 AC4: còn mật khẩu tạm thì chỉ thấy màn hình đổi mật khẩu
    page = <ChangePasswordPage user={user} onChanged={onPasswordChanged} onLogout={onLogout} />
  } else {
    page = <InternalHomePage user={user} onLogout={onLogout} />
  }
  return <div className="app-shell">{page}</div>
}