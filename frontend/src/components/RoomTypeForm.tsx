import { useState, type FormEvent } from 'react'
import type { RoomType, RoomTypePayload } from '../types/roomType'

type RoomTypeFormProps = {
  initial?: RoomType
  onSubmit: (payload: RoomTypePayload) => Promise<void>
  onCancel?: () => void
}

type FormState = {
  code: string
  name: string
  standardCapacity: string
  maxCapacity: string
  numberOfBeds: string
  description: string
}

function toFormState(roomType?: RoomType): FormState {
  return {
    code: roomType?.code ?? '',
    name: roomType?.name ?? '',
    standardCapacity: roomType ? String(roomType.standardCapacity) : '2',
    maxCapacity: roomType ? String(roomType.maxCapacity) : '2',
    numberOfBeds: roomType ? String(roomType.numberOfBeds) : '1',
    description: roomType?.description ?? '',
  }
}

/** S1-06 AC1, AC2: mã, tên, sức chứa tiêu chuẩn, sức chứa tối đa, số giường và mô tả. */
export function RoomTypeForm({ initial, onSubmit, onCancel }: RoomTypeFormProps) {
  const [form, setForm] = useState<FormState>(() => toFormState(initial))
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  function update(field: keyof FormState, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    const standardCapacity = Number(form.standardCapacity)
    const maxCapacity = Number(form.maxCapacity)
    const numberOfBeds = Number(form.numberOfBeds)

    if (!form.code.trim() || !form.name.trim()) {
      setError('Vui lòng nhập mã và tên loại phòng')
      return
    }
    if (![standardCapacity, maxCapacity, numberOfBeds].every((value) => Number.isInteger(value) && value >= 1)) {
      setError('Sức chứa và số giường phải là số nguyên từ 1 trở lên')
      return
    }
    // AC2: báo ngay trên giao diện, máy chủ vẫn kiểm tra lại.
    if (maxCapacity < standardCapacity) {
      setError(`Sức chứa tối đa (${maxCapacity}) không được nhỏ hơn sức chứa tiêu chuẩn (${standardCapacity})`)
      return
    }

    setIsSaving(true)
    try {
      await onSubmit({
        code: form.code.trim(),
        name: form.name.trim(),
        standardCapacity,
        maxCapacity,
        numberOfBeds,
        description: form.description.trim(),
      })
      if (!initial) setForm(toFormState())
    } catch (err) {
      // AC3: hiện đúng thông báo của máy chủ, ví dụ "Mã loại phòng DOI đã tồn tại..."
      setError(err instanceof Error ? err.message : 'Không lưu được loại phòng')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <form className="checkin-form" onSubmit={handleSubmit} noValidate>
      {error && <div className="alert" role="alert">{error}</div>}
      <label className="form-label">
        Mã loại phòng
        <input className="form-control" value={form.code} placeholder="DOI" maxLength={50}
          onChange={(e) => update('code', e.target.value)} />
      </label>
      <label className="form-label">
        Tên loại phòng
        <input className="form-control" value={form.name} placeholder="Phòng đôi" maxLength={80}
          onChange={(e) => update('name', e.target.value)} />
      </label>
      <label className="form-label">
        Sức chứa tiêu chuẩn (người)
        <input className="form-control" type="number" min={1} value={form.standardCapacity}
          onChange={(e) => update('standardCapacity', e.target.value)} />
      </label>
      <label className="form-label">
        Sức chứa tối đa (người)
        <input className="form-control" type="number" min={1} value={form.maxCapacity}
          onChange={(e) => update('maxCapacity', e.target.value)} />
      </label>
      <label className="form-label">
        Số giường
        <input className="form-control" type="number" min={1} value={form.numberOfBeds}
          onChange={(e) => update('numberOfBeds', e.target.value)} />
      </label>
      <label className="form-label">
        Mô tả
        <textarea className="form-control" rows={3} maxLength={500} value={form.description}
          onChange={(e) => update('description', e.target.value)} />
      </label>
      <button className="primary-button" type="submit" disabled={isSaving}>
        {isSaving ? 'Đang lưu...' : initial ? 'Lưu thay đổi' : 'Thêm loại phòng'}
      </button>
      {onCancel && (
        <button className="secondary-button" type="button" onClick={onCancel}>
          Huỷ
        </button>
      )}
      {initial && initial.roomCount > 0 && (
        <p className="checkin-note">
          Đổi tên sẽ cập nhật luôn tên loại phòng của {initial.roomCount} phòng đang gắn vào.
        </p>
      )}
    </form>
  )
}