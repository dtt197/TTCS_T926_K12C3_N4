import type { AuthError, LoginRequest, LoginResponse } from '../types/auth'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as AuthError | null
    throw new Error(error?.message ?? 'Không thể đăng nhập lúc này')
  }

  return (await response.json()) as LoginResponse
}
