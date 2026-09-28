
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
    <main className="login-layout">
      <section className="brand-panel" aria-label="HomeStay">
        <div>
          <p className="eyebrow">HomeStay operations</p>
          <h1>Welcome back.</h1>
          <p className="brand-copy">
            Một không gian làm việc gọn gàng cho đội ngũ vận hành lưu trú.
          </p>
        </div>
        <div className="brand-footer">
          <span className="brand-mark" aria-hidden="true">H</span>
          <span>Internal workspace</span>
        </div>
      </section>

      <section className="form-panel">
        <form className="login-form" onSubmit={handleSubmit}>
          <p className="eyebrow">Account recovery</p>
          <h2>Quên mật khẩu</h2>
          <p className="form-intro">
            Nhập email tài khoản để nhận liên kết đặt lại mật khẩu.
          </p>

          {error && <p className="error-message" role="alert">{error}</p>}

          {success ? (
            <p role="status">
              Nếu email thuộc tài khoản trong hệ thống, bạn sẽ nhận được
              liên kết đặt lại mật khẩu. Hãy kiểm tra hộp thư.
            </p>
          ) : (
            <>
              <div className="field">
                <label htmlFor="reset-email">Email</label>
                <input
                  id="reset-email"
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

          <p><a href="/">Quay lại đăng nhập</a></p>
        </form>
      </section>
    </main>
  )
}
