export type LoginRequest = {
  email: string
  password: string
}

export type LoginResponse = {
  userId: number
  fullName: string
  email: string
  role: string
}

export type AuthError = {
  code: string
  message: string
}
