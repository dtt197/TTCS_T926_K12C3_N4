import { useState, type FormEvent } from 'react'
import type { OperatingSettings, OperatingSettingsPayload } from '../types/settings'

const MAX_TIERS = 3

type TierRow = { hours: string; percent: string }

type FormState = {
  homestayName: string
  address: string
  phone: string
  email: string
  checkInTime: string
  checkOutTime: string
  lateCheckoutFeePerHour: string
  extraPersonFee: string
  tiers: TierRow[]
}

type SettingsFormProps = {
  initial: OperatingSettings
  onSubmit: (payload: OperatingSettingsPayload) => Promise<void>
}

function toFormState(settings: OperatingSettings): FormState {
  return {
    homestayName: settings.homestayName,
    address: settings.address ?? '',
    phone: settings.phone ?? '',
    email: settings.email ?? '',
    checkInTime: settings.checkInTime.slice(0, 5),
    checkOutTime: settings.checkOutTime.slice(0, 5),
    lateCheckoutFeePerHour: String(settings.lateCheckoutFeePerHour),
    extraPersonFee: String(settings.extraPersonFee),
    tiers: settings.cancellationTiers.map((tier) => ({
      hours: String(tier.hoursBeforeCheckIn),
      percent: String(tier.refundPercent),
    })),
  }
}

const isWholeNumber = (value: string) => /^\d+$/.test(value.trim())

/** S1-09 AC1, AC2: thông tin homestay, giờ nhận/trả phòng, phụ thu và tối đa 3 mốc chính sách huỷ. */
export function SettingsForm({ initial, onSubmit }: SettingsFormProps) {
  const [form, setForm] = useState<FormState>(() => toFormState(initial))
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  function update(field: Exclude<keyof FormState, 'tiers'>, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  function updateTier(index: number, field: keyof TierRow, value: string) {
    setForm((current) => ({
      ...current,
      tiers: current.tiers.map((tier, i) => (i === index ? { ...tier, [field]: value } : tier)),
    }))
  }

  function addTier() {
    setForm((current) => ({ ...current, tiers: [...current.tiers, { hours: '', percent: '' }] }))
  }

  function removeTier(index: number) {
    setForm((current) => ({ ...current, tiers: current.tiers.filter((_, i) => i !== index) }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    if (!form.homestayName.trim()) {
      setError('Vui lòng nhập tên homestay')
      return
    }
    if (!isWholeNumber(form.lateCheckoutFeePerHour) || !isWholeNumber(form.extraPersonFee)) {
      setError('Phụ thu phải là số nguyên từ 0 trở lên (VND)')
      return
    }
    if (form.tiers.length === 0 || form.tiers.some((tier) => !isWholeNumber(tier.hours) || !isWholeNumber(tier.percent))) {
      setError('Mỗi mốc huỷ phải có số giờ và tỷ lệ hoàn là số nguyên')
      return
    }

    setIsSaving(true)
    try {
      // AC3 (chồng lấn, giảm dần) do máy chủ kiểm tra và trả thông báo cụ thể.
      await onSubmit({
        homestayName: form.homestayName.trim(),
        address: form.address.trim(),
        phone: form.phone.trim(),
        email: form.email.trim(),
        checkInTime: form.checkInTime,
        checkOutTime: form.checkOutTime,
        lateCheckoutFeePerHour: Number(form.lateCheckoutFeePerHour),
        extraPersonFee: Number(form.extraPersonFee),
        cancellationTiers: form.tiers.map((tier) => ({
          hoursBeforeCheckIn: Number(tier.hours),
          refundPercent: Number(tier.percent),
        })),
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không lưu được tham số')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <form className="checkin-form" onSubmit={handleSubmit} noValidate>
      {error && <div className="alert" role="alert">{error}</div>}

      <label className="form-label">
        Tên homestay
        <input className="form-control" value={form.homestayName} maxLength={150}
          onChange={(e) => update('homestayName', e.target.value)} />
      </label>
      <label className="form-label">
        Địa chỉ
        <input className="form-control" value={form.address} maxLength={255}
          onChange={(e) => update('address', e.target.value)} />
      </label>
      <label className="form-label">
        Số điện thoại
        <input className="form-control" value={form.phone} placeholder="0912345678" maxLength={10}
          onChange={(e) => update('phone', e.target.value)} />
      </label>
      <label className="form-label">
        Email
        <input className="form-control" type="email" value={form.email} maxLength={255}
          onChange={(e) => update('email', e.target.value)} />
      </label>

      <label className="form-label">
        Giờ nhận phòng
        <input className="form-control" type="time" value={form.checkInTime}
          onChange={(e) => update('checkInTime', e.target.value)} />
      </label>
      <label className="form-label">
        Giờ trả phòng
        <input className="form-control" type="time" value={form.checkOutTime}
          onChange={(e) => update('checkOutTime', e.target.value)} />
      </label>
      <label className="form-label">
        Phụ thu trả muộn (VND / giờ)
        <input className="form-control" inputMode="numeric" value={form.lateCheckoutFeePerHour}
          onChange={(e) => update('lateCheckoutFeePerHour', e.target.value)} />
      </label>
      <label className="form-label">
        Phụ thu thêm người (VND / người / đêm)
        <input className="form-control" inputMode="numeric" value={form.extraPersonFee}
          onChange={(e) => update('extraPersonFee', e.target.value)} />
      </label>

      <h3>Chính sách huỷ (tối đa {MAX_TIERS} mốc)</h3>
      <p className="checkin-note">
        Càng sát giờ nhận phòng thì tỷ lệ hoàn cọc phải càng thấp. Ví dụ: trước 72 giờ hoàn 100%, trước 24 giờ hoàn 50%.
      </p>
      {form.tiers.map((tier, index) => (
        <div className="user-actions" key={index}>
          <label className="form-label">
            Huỷ trước (giờ)
            <input className="form-control" inputMode="numeric" value={tier.hours}
              onChange={(e) => updateTier(index, 'hours', e.target.value)} />
          </label>
          <label className="form-label">
            Hoàn cọc (%)
            <input className="form-control" inputMode="numeric" value={tier.percent}
              onChange={(e) => updateTier(index, 'percent', e.target.value)} />
          </label>
          <button className="secondary-button" type="button" disabled={form.tiers.length === 1}
            onClick={() => removeTier(index)}>
            Bỏ mốc
          </button>
        </div>
      ))}
      <button className="secondary-button" type="button" disabled={form.tiers.length >= MAX_TIERS} onClick={addTier}>
        + Thêm mốc
      </button>

      <button className="primary-button" type="submit" disabled={isSaving}>
        {isSaving ? 'Đang lưu...' : 'Lưu tham số'}
      </button>
      <p className="checkin-note">
        Mỗi lần lưu tạo phiên bản mới, ghi người sửa và thời điểm; chỉ áp cho booking tạo sau thời điểm lưu.
      </p>
    </form>
  )
}