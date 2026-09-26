import { useEffect, useState } from 'react'
import { apiRequest } from '../services/apiClient'
import { logout, refreshAccessToken } from '../services/authService'
import { clearAccessToken, getAccessToken } from '../services/tokenStore'
import type { LoginResponse, UserProfile } from '../types/auth'

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

function profileWithToken(profile: UserProfile): LoginResponse {
  return {
    ...profile,
    accessToken: getAccessToken() ?? '',
    expiresIn: 0,
  }
}

export function useAuth() {
  const [user, setUser] = useState<LoginResponse | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  useEffect(() => {
    let isMounted = true

    async function restoreSession() {
      try {
        await refreshAccessToken()
        const profile = await apiRequest<UserProfile>('/api/internal/me')
        if (isMounted) {
          setUser(profileWithToken(profile))
          setStatus('authenticated')
        }
      } catch {
        clearAccessToken()
        if (isMounted) {
          setUser(null)
          setStatus('unauthenticated')
        }
      }
    }

    void restoreSession()

    function handleSessionExpired() {
      clearAccessToken()
      setUser(null)
      setStatus('unauthenticated')
    }

    window.addEventListener('auth:expired', handleSessionExpired)
    return () => {
      isMounted = false
      window.removeEventListener('auth:expired', handleSessionExpired)
    }
  }, [])

  function handleLogin(nextUser: LoginResponse) {
    setUser(nextUser)
    setStatus('authenticated')
  }

  async function handleLogout() {
  await logout()
  clearAccessToken()
  setUser(null)
  setStatus('unauthenticated')
}

  return { user, status, handleLogin, handleLogout }
}