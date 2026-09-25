import { useState } from 'react'
import './App.css'
import { AppRoutes } from './routes/AppRoutes'
import type { LoginResponse } from './types/auth'

function App() {
  const [user, setUser] = useState<LoginResponse | null>(null)

  return (
    <AppRoutes user={user} onLogin={setUser} onLogout={() => setUser(null)} />
  )
}

export default App
