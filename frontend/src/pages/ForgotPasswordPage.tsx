
import { useState, type FormEvent } from 'react'
import { forgotPassword } from '../services/authService'
import './AuthPages.css'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setIsSubmitting(true)

    try {
      await forgotPassword(email)
      setSuccess(true)
    } catch (submissionError) {
      setError(
        submissionError instanceof Error
          ? submissionError.message
          : 'Không thể gửi yêu cầu lúc này',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="login-layout login-page forgot-password-login-page">
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

      <section className="form-panel" aria-labelledby="forgot-title">
        <form className="login-form forgot-form" onSubmit={handleSubmit} noValidate>
          <p className="eyebrow">Account recovery</p>
          <h2 id="forgot-title">Quên mật khẩu</h2>
          <p className="form-intro">Nhập email đã đăng ký để nhận hướng dẫn đặt lại mật khẩu.</p>

          {error && <p className="error-message" role="alert">{error}</p>}

          {success ? (
            <p className="forgot-success" role="status">
              Nếu email thuộc tài khoản trong hệ thống, bạn sẽ nhận được liên kết đặt lại mật khẩu. Hãy kiểm tra hộp thư.
            </p>
          ) : (
            <>
              <div className="field">
                <label htmlFor="reset-email">Email</label>
                <input
                  id="reset-email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  required
                />
              </div>

              <button
                className="submit-button"
                type="submit"
                disabled={isSubmitting}
              >
                {isSubmitting ? 'Đang gửi...' : 'Gửi liên kết đặt lại mật khẩu'}
              </button>
            </>
          )}

          <a className="forgot-back-link" href="/dang-nhap">Quay lại đăng nhập</a>
        </form>
      </section>
    </main>
  )
}
