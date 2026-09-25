import type { AuthError, LoginRequest, LoginResponse, RefreshResponse } from '../types/auth'
import { setAccessToken } from './tokenStore'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as AuthError | null
    throw new Error(error?.message ?? 'Không thể đăng nhập lúc này')
  }

  const result = (await response.json()) as LoginResponse
  setAccessToken(result.accessToken)
  return result
}

export async function refreshAccessToken(): Promise<RefreshResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error('Phiên đăng nhập đã hết hạn')
  }

  const result = (await response.json()) as RefreshResponse
  setAccessToken(result.accessToken)
  return result
}
