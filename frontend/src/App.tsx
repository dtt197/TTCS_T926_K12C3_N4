import './App.css'
import { useAuth } from './hooks/useAuth'
import { AppRoutes } from './routes/AppRoutes'

function App() {
  const { user, status, handleLogin, handleLogout, handlePasswordChanged } = useAuth()

  if (status === 'loading') {
    return <main className="app-shell" aria-busy="true" />
  }

  return (
    <AppRoutes
      user={user}
      onLogin={handleLogin}
      onLogout={handleLogout}
      onPasswordChanged={handlePasswordChanged}
    />
  )
}

export default App