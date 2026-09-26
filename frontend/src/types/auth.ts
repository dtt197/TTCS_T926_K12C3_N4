export type LoginRequest = {
  email: string
  password: string
}

export type LoginResponse = {
  userId: number
  fullName: string
  email: string
  role: string
accessToken: string
  expiresIn: number
  mustChangePassword: boolean
}

export type RefreshResponse = {
  accessToken: string
  expiresIn: number
}

export type UserProfile = Omit<LoginResponse, 'accessToken' | 'expiresIn'>

export type AuthError = {
  code: string
  message: string
}
