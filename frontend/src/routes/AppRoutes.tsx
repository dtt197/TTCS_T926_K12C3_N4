import { InternalHomePage } from '../pages/InternalHomePage'
import { LoginPage } from '../pages/LoginPage'
import type { LoginResponse } from '../types/auth'

type AppRoutesProps = {
  user: LoginResponse | null
  onLogin: (user: LoginResponse) => void
  onLogout: () => void
}

export function AppRoutes({ user, onLogin, onLogout }: AppRoutesProps) {
  return (
    <div className="app-shell">
      {user ? <InternalHomePage user={user} onLogout={onLogout} /> : <LoginPage onLogin={onLogin} />}
    </div>
  )
}
