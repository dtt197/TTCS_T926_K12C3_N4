import { useState, type FormEvent } from 'react'
import { ROLE_LABELS, type StaffRole, type StaffUser, type UpdateUserPayload } from '../types/user'

type EditUserFormProps = {
  user: StaffUser
  isSelf: boolean
  onSave: (payload: UpdateUserPayload) => Promise<void>
  onCancel: () => void
}

/** S1-02 Lát 4: sửa họ tên, số điện thoại, vai trò. Email không đổi được. */
export function EditUserForm({ user, isSelf, onSave, onCancel }: EditUserFormProps) {
  const [form, setForm] = useState<UpdateUserPayload>({
    fullName: user.fullName,
    phone: user.phone ?? '',
    role: user.role,
  })
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const roleChanged = form.role !== user.role

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    if (!form.fullName.trim()) {
      setError('Vui lòng nhập họ tên')
      return
    }
    if (form.phone.trim() && !/^0\d{9}$/.test(form.phone.trim())) {
      setError('Số điện thoại gồm 10 chữ số, bắt đầu bằng 0')
      return
    }
    setIsSaving(true)
    try {
      await onSave(form)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không lưu được thay đổi')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <form className="checkin-form" onSubmit={handleSubmit} noValidate>
      {error && <div className="alert" role="alert">{error}</div>}
      <label className="form-label">
        Email (không đổi được)
        <input className="form-control" value={user.email} disabled />
      </label>
      <label className="form-label">
        Họ tên
        <input className="form-control" value={form.fullName}
          onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
      </label>
      <label className="form-label">
        Số điện thoại
        <input className="form-control" value={form.phone} placeholder="0912345678"
          onChange={(e) => setForm({ ...form, phone: e.target.value })} />
      </label>
      <label className="form-label">
        Vai trò
        <select className="form-control" value={form.role} disabled={isSelf}
          onChange={(e) => setForm({ ...form, role: e.target.value as StaffRole })}>
          {(Object.keys(ROLE_LABELS) as StaffRole[]).map((role) => (
            <option key={role} value={role}>{ROLE_LABELS[role]}</option>
          ))}
        </select>
      </label>
      {isSelf && <p className="checkin-note">Bạn không thể tự đổi vai trò của chính mình.</p>}
      {roleChanged && <p className="checkin-note">Người này sẽ phải đăng nhập lại để nhận vai trò mới.</p>}
      <button className="primary-button" type="submit" disabled={isSaving}>
        {isSaving ? 'Đang lưu...' : 'Lưu thay đổi'}
      </button>
      <button className="secondary-button" type="button" onClick={onCancel}>
        Huỷ
      </button>
    </form>
  )
}