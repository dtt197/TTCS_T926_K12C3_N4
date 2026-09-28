import { useState, type FormEvent } from 'react'
import type { Amenity, AmenityPayload } from '../types/amenity'

/** Gợi ý biểu tượng; vẫn gõ emoji khác được. */
const ICON_SUGGESTIONS = ['❄️', '📶', '🌇', '📺', '🚿', '🧊', '🛁', '☕', '🅿️', '🧺', '🔒', '💨']

type AmenityFormProps = {
  initial?: Amenity
  onSubmit: (payload: AmenityPayload) => Promise<void>
  onCancel?: () => void
}

/** S1-08 AC1: tiện nghi gồm mã, tên và biểu tượng. */
export function AmenityForm({ initial, onSubmit, onCancel }: AmenityFormProps) {
  const [form, setForm] = useState<AmenityPayload>(() => ({
    code: initial?.code ?? '',
    name: initial?.name ?? '',
    icon: initial?.icon ?? '',
  }))
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  function update(field: keyof AmenityPayload, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    if (!form.code.trim() || !form.name.trim() || !form.icon.trim()) {
      setError('Vui lòng nhập mã, tên và chọn biểu tượng')
      return
    }
    setIsSaving(true)
    try {
      await onSubmit({ code: form.code.trim(), name: form.name.trim(), icon: form.icon.trim() })
      if (!initial) setForm({ code: '', name: '', icon: '' })
    } catch (err) {
      // Hiện đúng thông báo của máy chủ, ví dụ "Mã tiện nghi WIFI đã tồn tại..."
      setError(err instanceof Error ? err.message : 'Không lưu được tiện nghi')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <form className="checkin-form" onSubmit={handleSubmit} noValidate>
      {error && <div className="alert" role="alert">{error}</div>}
      <label className="form-label">
        Mã tiện nghi
        <input className="form-control" value={form.code} placeholder="DIEU_HOA" maxLength={50}
          onChange={(e) => update('code', e.target.value)} />
      </label>
      <label className="form-label">
        Tên tiện nghi
        <input className="form-control" value={form.name} placeholder="Điều hoà" maxLength={100}
          onChange={(e) => update('name', e.target.value)} />
      </label>
      <label className="form-label">
        Biểu tượng
        <input className="form-control" value={form.icon} placeholder="❄️" maxLength={20}
          onChange={(e) => update('icon', e.target.value)} />
      </label>
      <div className="user-actions" aria-label="Gợi ý biểu tượng">
        {ICON_SUGGESTIONS.map((icon) => (
          <button key={icon} type="button"
            className={form.icon === icon ? 'primary-button' : 'secondary-button'}
            onClick={() => update('icon', icon)}>
            {icon}
          </button>
        ))}
      </div>
      <button className="primary-button" type="submit" disabled={isSaving}>
        {isSaving ? 'Đang lưu...' : initial ? 'Lưu thay đổi' : 'Thêm tiện nghi'}
      </button>
      {onCancel && (
        <button className="secondary-button" type="button" onClick={onCancel}>
          Huỷ
        </button>
      )}
    </form>
  )
}