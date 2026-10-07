import { useState, type FormEvent } from 'react'
import { login } from '../services/authService'
import type { LoginResponse } from '../types/auth'
import './AuthPages.css'
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
    <main className="login-layout login-page">
      <section className="brand-panel" aria-label="HomeStay">
        <div className="login-brand-content">
          <div className="login-brand-identity">
            <span className="login-wordmark">HomeStay</span>
            <span className="login-tagline">Nghỉ dưỡng như ở nhà</span>
          </div>
          <p className="eyebrow">Homestay operations</p>
          <h1>
            Nơi mỗi
            <br />
            kỳ nghỉ
            <br />
            <span>đều đặc biệt</span>
          </h1>
          <p className="brand-copy">
            Quản lý homestay dễ dàng,
            <br />
            trải nghiệm lưu trú trọn vẹn hơn.
          </p>
        </div>

        <ul className="login-features">
          <li>
            <span className="login-feature-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <rect x="3.5" y="5" width="17" height="16" rx="2" />
                <path d="M7.5 3v4M16.5 3v4M3.5 10h17M8 14h3M8 17h6" />
              </svg>
            </span>
            <span>
              <strong>Quản lý đặt phòng</strong>
              <small>Nhanh chóng, chính xác</small>
            </span>
          </li>
          <li>
            <span className="login-feature-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path d="M4 19.5h16M6.5 16V11M12 16V5M17.5 16V8" />
                <path d="m5 8 5-4 4 2 5-4" />
              </svg>
            </span>
            <span>
              <strong>Vận hành hiệu quả</strong>
              <small>Dành cho mọi vai trò</small>
            </span>
          </li>
          <li>
            <span className="login-feature-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path d="M12 3.5 19 6v5.6c0 4.2-2.8 7.1-7 8.9-4.2-1.8-7-4.7-7-8.9V6l7-2.5Z" />
                <path d="m9 12 2 2 4-4" />
              </svg>
            </span>
            <span>
              <strong>An toàn và bảo mật</strong>
              <small>Bảo vệ dữ liệu của bạn</small>
            </span>
          </li>
        </ul>

        <div className="brand-footer">
          <span>Home Away From Home</span>
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
          <p className="forgot-password-link">
            <a href="/forgot-password">Quên mật khẩu?</a>
          </p>

          <button className="submit-button" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </button>

          <div className="customer-links">
            <p>Bạn là khách hàng đang tìm phòng nghỉ?</p>
            <a href="/danh-sach-loai-phong">
              Khám phá danh sách loại phòng &amp; Đặt ngay <span aria-hidden="true">→</span>
            </a>
            <a href="/tra-cuu-dat-phong">
              Đã đặt phòng? Tra cứu booking <span aria-hidden="true">→</span>
            </a>
          </div>
        </form>
      </section>
    </main>
  )
}
