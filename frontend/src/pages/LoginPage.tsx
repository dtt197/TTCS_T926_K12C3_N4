import { useState, type FormEvent } from 'react'
import { login } from '../services/authService'
import type { LoginResponse } from '../types/auth'

type LoginPageProps = {
  onLogin: (user: LoginResponse) => void
}

export function LoginPage({ onLogin }: LoginPageProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setIsSubmitting(true)

    try {
      const user = await login({ email, password })
      onLogin(user)
    } catch (submissionError) {
      setError(submissionError instanceof Error ? submissionError.message : 'Không thể đăng nhập lúc này')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="login-layout">
      <section className="brand-panel" aria-label="HomeStay">
        <div>
          <p className="eyebrow">HomeStay operations</p>
          <h1>Welcome back.</h1>
          <p className="brand-copy">Một không gian làm việc gọn gàng cho đội ngũ vận hành lưu trú.</p>
        </div>
        <div className="brand-footer">
          <span className="brand-mark" aria-hidden="true">H</span>
          <span>Internal workspace</span>
        </div>
      </section>

      <section className="form-panel">
        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <p className="eyebrow">Sign in</p>
          <h2>Đăng nhập</h2>
          <p className="form-intro">Sử dụng tài khoản nội bộ của bạn để tiếp tục.</p>

          {error && <p className="error-message" role="alert">{error}</p>}

          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="password">Mật khẩu</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </div>

          <button className="submit-button" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </button>
        </form>
      </section>
    </main>
  )
}
