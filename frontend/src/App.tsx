import './App.css'
import { useAuth } from './hooks/useAuth'
import { AppRoutes } from './routes/AppRoutes'

function App() {
  const { user, status, handleLogin, handleLogout } = useAuth()

  if (status === 'loading') {
    return <main className="app-shell" aria-busy="true" />
  }

  return (
    <AppRoutes
      user={user}
      onLogin={handleLogin}
      onLogout={handleLogout}
    />
  )
}

export default App