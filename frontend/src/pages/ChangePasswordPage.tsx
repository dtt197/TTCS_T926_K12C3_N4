import { useState, type FormEvent } from 'react'
import { changePassword } from '../services/accountService'
import type { LoginResponse } from '../types/auth'
import './AuthPages.css'

type ChangePasswordPageProps = {
  user: LoginResponse
  onChanged: () => void
  onLogout: () => void
}

const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,100}$/

/** S1-02 AC4: đăng nhập lần đầu bằng mật khẩu tạm thì phải đổi mật khẩu trước khi dùng hệ thống. */
export function ChangePasswordPage({ user, onChanged, onLogout }: ChangePasswordPageProps) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    if (!PASSWORD_RULE.test(newPassword)) {
      setError('Mật khẩu mới tối thiểu 8 ký tự, gồm cả chữ và số')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('Nhập lại mật khẩu mới không khớp')
      return
    }
    setIsSubmitting(true)
    try {
      await changePassword({ currentPassword, newPassword })
      onChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không đổi được mật khẩu')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="login-layout">
      <section className="brand-panel" aria-label="HomeStay">
        <div>
          <p className="eyebrow">HomeStay operations</p>
          <h1>Chào {user.fullName}.</h1>
          <p className="brand-copy">
            Đây là lần đăng nhập đầu tiên bằng mật khẩu tạm. Hãy đặt mật khẩu của riêng bạn để tiếp tục.
          </p>
        </div>
        <div className="brand-footer">
          <span className="brand-mark" aria-hidden="true">H</span>
          <span>Internal workspace</span>
        </div>
      </section>

      <section className="form-panel">
        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <p className="eyebrow">Bảo mật tài khoản</p>
          <h2>Đổi mật khẩu</h2>
          <p className="form-intro">Mật khẩu mới tối thiểu 8 ký tự, gồm cả chữ và số.</p>

          {error && <p className="error-message" role="alert">{error}</p>}

          <div className="field">
            <label htmlFor="current-password">Mật khẩu tạm (trong email)</label>
            <input id="current-password" type="password" autoComplete="current-password"
              value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} required />
          </div>

          <div className="field">
            <label htmlFor="new-password">Mật khẩu mới</label>
            <input id="new-password" type="password" autoComplete="new-password"
              value={newPassword} onChange={(event) => setNewPassword(event.target.value)} required />
          </div>

          <div className="field">
            <label htmlFor="confirm-password">Nhập lại mật khẩu mới</label>
            <input id="confirm-password" type="password" autoComplete="new-password"
              value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} required />
          </div>

          <button className="submit-button" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Đang lưu...' : 'Đổi mật khẩu'}
          </button>
          <button className="secondary-button" type="button" onClick={onLogout}>
            Đăng xuất
          </button>
        </form>
      </section>
    </main>
  )
}