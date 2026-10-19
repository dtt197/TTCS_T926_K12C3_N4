
import { useState, type FormEvent } from 'react'
import { resetPassword } from '../services/authService'
import './AuthPages.css'

export function ResetPasswordPage() {
  const token = new URLSearchParams(window.location.search).get('token')

  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')

    if (!token) {
      setError('Liên kết đặt lại mật khẩu không hợp lệ.')
      return
    }

    if (password !== confirmPassword) {
      setError('Mật khẩu xác nhận không khớp.')
      return
    }

    setIsSubmitting(true)

    try {
      await resetPassword(token, password)
      setSuccess(true)
    } catch (submissionError) {
      setError(
        submissionError instanceof Error
          ? submissionError.message
          : 'Không thể đặt lại mật khẩu',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="login-layout login-page reset-password-login-page">
      <section className="brand-panel" aria-label="HomeStay">
        <div className="login-brand-content">
          <div className="login-brand-identity">
            <span className="login-wordmark">HomeStay</span>
            <span className="login-tagline">Nghỉ dưỡng như ở nhà</span>
          </div>
          <p className="eyebrow">Homestay operations</p>
          <h1>Nơi mỗi kỳ nghỉ <span>đều đặc biệt</span></h1>
          <p className="brand-copy">Quản lý homestay dễ dàng,<br />trải nghiệm lưu trú trọn vẹn hơn.</p>
        </div>

        <ul className="login-features">
          <li><span className="login-feature-icon" aria-hidden="true">⌂</span><span><strong>Quản lý đặt phòng</strong><small>Nhanh chóng, chính xác</small></span></li>
          <li><span className="login-feature-icon" aria-hidden="true">↗</span><span><strong>Vận hành hiệu quả</strong><small>Dành cho mọi vai trò</small></span></li>
          <li><span className="login-feature-icon" aria-hidden="true">✓</span><span><strong>An toàn và bảo mật</strong><small>Bảo vệ dữ liệu của bạn</small></span></li>
        </ul>

        <div className="brand-footer"><span>Home Away From Home</span></div>
      </section>

      <section className="form-panel" aria-labelledby="reset-title">
        <form className="login-form reset-password-form" onSubmit={handleSubmit} noValidate>
          <p className="eyebrow">ACCOUNT RECOVERY</p>
          <h2 id="reset-title">Đặt lại mật khẩu</h2>
          {error && <p className="error-message" role="alert">{error}</p>}

          {!token ? (
            <p className="reset-password-notice">Liên kết không hợp lệ hoặc thiếu mã đặt lại mật khẩu.</p>
          ) : success ? (
            <p className="reset-password-success" role="status">
              Đặt lại mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới.
            </p>
          ) : (
            <>
              <p className="form-intro">Tạo mật khẩu mới để tiếp tục quản lý những kỳ nghỉ của bạn.</p>

              <div className="field">
                <label htmlFor="new-password">Mật khẩu mới</label>
                <input
                  id="new-password"
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                />
              </div>

              <div className="field">
                <label htmlFor="confirm-password">Xác nhận mật khẩu mới</label>
                <input
                  id="confirm-password"
                  type="password"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  required
                />
              </div>

              <button className="submit-button" type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Đang xử lý...' : 'Đặt lại mật khẩu'}
              </button>
            </>
          )}

          <a className="reset-password-back-link" href="/dang-nhap">Quay lại đăng nhập</a>
        </form>
      </section>
    </main>
  )
}
