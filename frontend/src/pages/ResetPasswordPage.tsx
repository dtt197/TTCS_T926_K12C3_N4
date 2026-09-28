
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
            <h2>Đặt lại mật khẩu</h2>

            {error && <p className="error-message" role="alert">{error}</p>}

            {!token ? (
                <p>Liên kết không hợp lệ hoặc thiếu mã đặt lại mật khẩu.</p>
            ) : success ? (
                <p role="status">
                Đặt lại mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới.
                </p>
            ) : (
                <>
                <p className="form-intro">Nhập mật khẩu mới cho tài khoản của bạn.</p>

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

                <button
                    className="submit-button"
                    type="submit"
                    disabled={isSubmitting}
                >
                    {isSubmitting ? 'Đang xử lý...' : 'Đặt lại mật khẩu'}
                </button>
                </>
            )}

            <p><a href="/">Quay lại đăng nhập</a></p>
            </form>
        </section>
        </main>
    )
    }
