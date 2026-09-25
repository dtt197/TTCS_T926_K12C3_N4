import { refreshAccessToken } from './authService'
import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export class AuthSessionExpiredError extends Error {
  constructor() {
    super('Phiên đăng nhập đã hết hạn')
    this.name = 'AuthSessionExpiredError'
  }
}

let refreshPromise: Promise<string> | null = null

function notifySessionExpired(): void {
  clearAccessToken()
  window.dispatchEvent(new Event('auth:expired'))
}

async function refreshOnce(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken()
      .then(({ accessToken }) => {
        setAccessToken(accessToken)
        return accessToken
      })
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  hasRetried = false,
): Promise<T> {
  const headers = new Headers(init.headers)
  const token = getAccessToken()

  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  })

  if (response.status !== 401 || hasRetried) {
    if (!response.ok) {
      throw new Error(`API request failed with status ${response.status}`)
    }
    return (await response.json()) as T
  }

  try {
    await refreshOnce()
  } catch {
    notifySessionExpired()
    throw new AuthSessionExpiredError()
  }

  return apiRequest<T>(path, init, true)
}