import { useState, type FormEvent } from 'react'
import { ROLE_LABELS, type CreateUserPayload, type StaffRole } from '../types/user'

type CreateUserFormProps = {
  onSubmit: (payload: CreateUserPayload) => Promise<void>
}

const EMPTY_FORM: CreateUserPayload = {
  fullName: '',
  email: '',
  phone: '',
  role: 'RECEPTIONIST',
  active: true,
}

/** S1-02 AC1, AC2: biểu mẫu họ tên, email, số điện thoại, vai trò, trạng thái hoạt động. */
export function CreateUserForm({ onSubmit }: CreateUserFormProps) {
  const [form, setForm] = useState<CreateUserPayload>(EMPTY_FORM)
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    if (!form.fullName.trim() || !form.email.trim()) {
      setError('Vui lòng nhập họ tên và email')
      return
    }
    if (form.phone.trim() && !/^0\d{9}$/.test(form.phone.trim())) {
      setError('Số điện thoại gồm 10 chữ số, bắt đầu bằng 0')
      return
    }
    setIsSaving(true)
    try {
      await onSubmit(form)
      setForm(EMPTY_FORM)
    } catch (err) {
      // AC3: hiện đúng thông báo của máy chủ, vd "Email ... đã được dùng cho một tài khoản khác"
      setError(err instanceof Error ? err.message : 'Không tạo được tài khoản')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <form className="checkin-form" onSubmit={handleSubmit} noValidate>
      {error && <div className="alert" role="alert">{error}</div>}
      <label className="form-label">
        Họ tên
        <input className="form-control" value={form.fullName} placeholder="Nguyễn Văn An"
          onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
      </label>
      <label className="form-label">
        Email
        <input className="form-control" type="email" value={form.email} placeholder="letan@homestay.local"
          onChange={(e) => setForm({ ...form, email: e.target.value })} />
      </label>
      <label className="form-label">
        Số điện thoại
        <input className="form-control" value={form.phone} placeholder="0912345678"
          onChange={(e) => setForm({ ...form, phone: e.target.value })} />
      </label>
      <label className="form-label">
        Vai trò
        <select className="form-control" value={form.role}
          onChange={(e) => setForm({ ...form, role: e.target.value as StaffRole })}>
          {(Object.keys(ROLE_LABELS) as StaffRole[]).map((role) => (
            <option key={role} value={role}>{ROLE_LABELS[role]}</option>
          ))}
        </select>
      </label>
      <label className="form-check">
        <input type="checkbox" checked={form.active}
          onChange={(e) => setForm({ ...form, active: e.target.checked })} />
        Đang hoạt động
      </label>
      <button className="primary-button" type="submit" disabled={isSaving}>
        {isSaving ? 'Đang tạo...' : 'Tạo tài khoản'}
      </button>
      <p className="checkin-note">Mật khẩu tạm sẽ được gửi qua email, nhân viên phải đổi ở lần đăng nhập đầu tiên.</p>
    </form>
  )
}