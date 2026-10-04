import { useState, type FormEvent } from 'react'
import type { PriceOverride, PriceOverridePayload } from '../types/priceOverride'
import type { RoomType } from '../types/roomType'
import { PriceOverridePreview } from './PriceOverridePreview'

type PriceOverrideFormProps = {
  initial?: PriceOverride
  roomTypes: RoomType[]
  onSubmit: (payload: PriceOverridePayload) => Promise<void>
  onCancel?: () => void
}

type FormState = {
  name: string
  roomTypeId: string
  startDate: string
  endDate: string
  pricePerNight: string
}

function toFormState(priceOverride?: PriceOverride): FormState {
  return {
    name: priceOverride?.name ?? '',
    roomTypeId: priceOverride ? String(priceOverride.roomTypeId) : '',
    startDate: priceOverride?.startDate ?? '',
    endDate: priceOverride?.endDate ?? '',
    pricePerNight: priceOverride ? String(priceOverride.pricePerNight) : '',
  }
}

const money = new Intl.NumberFormat('vi-VN')

function holidayDates() {
  const today = new Date()
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  let start = new Date(today.getFullYear(), 3, 30)
  if (start < todayStart) start = new Date(today.getFullYear() + 1, 3, 30)
  const end = new Date(start.getFullYear(), 4, 1)
  const toIsoDate = (date: Date) =>
    `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return { startDate: toIsoDate(start), endDate: toIsoDate(end) }
}

// Đã đổi tên từ useHolidaySample thành getHolidaySample để tránh vi phạm Rules of Hooks
function getHolidaySamplePrice(roomType: RoomType) {
  const basePrice = roomType.weekendPrice ?? roomType.weekdayPrice
  if (!basePrice || basePrice <= 0) return null
  // Giá mẫu tham khảo: cộng 20% so với giá cuối tuần, làm tròn đến 10.000 VND.
  return Math.round((basePrice * 1.2) / 10_000) * 10_000
}

/** S2-02 AC1: tên đợt, khoảng ngày áp dụng, loại phòng và giá một đêm. */
export function PriceOverrideForm({ initial, roomTypes, onSubmit, onCancel }: PriceOverrideFormProps) {
  const [form, setForm] = useState<FormState>(() => toFormState(initial))
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  function update(field: keyof FormState, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  const price = /^\d+$/.test(form.pricePerNight.trim()) ? Number(form.pricePerNight.trim()) : null

  function handleUseHolidaySample(roomType: RoomType) {
    const samplePrice = getHolidaySamplePrice(roomType)
    if (samplePrice === null) return
    setForm({
      name: 'Lễ 30/4 – 1/5',
      roomTypeId: String(roomType.id),
      ...holidayDates(),
      pricePerNight: String(samplePrice),
    })
    setError(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    if (!form.name.trim() || !form.roomTypeId || !form.startDate || !form.endDate) {
      setError('Vui lòng nhập tên đợt, chọn loại phòng và khoảng ngày')
      return
    }
    // Ngày dạng yyyy-MM-dd nên so sánh chuỗi là đúng thứ tự thời gian. Máy chủ vẫn kiểm tra lại.
    if (form.endDate < form.startDate) {
      setError('Ngày kết thúc không được trước ngày bắt đầu')
      return
    }
    if (price === null || price <= 0) {
      setError('Giá một đêm phải là số nguyên lớn hơn 0 (VND)')
      return
    }

    setIsSaving(true)
    try {
      await onSubmit({
        name: form.name.trim(),
        roomTypeId: Number(form.roomTypeId),
        startDate: form.startDate,
        endDate: form.endDate,
        pricePerNight: price,
      })
      if (!initial) setForm(toFormState())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không lưu được giá đè')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <form className="checkin-form" onSubmit={handleSubmit} noValidate>
      {error && <div className="alert" role="alert">{error}</div>}
      <label className="form-label">
        Tên đợt
        <input className="form-control" value={form.name} placeholder="Lễ 30/4 – 1/5" maxLength={100}
          onChange={(e) => update('name', e.target.value)} />
      </label>
      <label className="form-label">
        Loại phòng
        <select className="form-control" value={form.roomTypeId}
          onChange={(e) => update('roomTypeId', e.target.value)}>
          <option value="">Chọn loại phòng...</option>
          {roomTypes.map((roomType) => (
            <option key={roomType.id} value={roomType.id}>
              {roomType.code} · {roomType.name}{roomType.active ? '' : ' (ngừng bán)'}
            </option>
          ))}
        </select>
      </label>
      <label className="form-label">
        Từ ngày (đêm đầu tiên)
        <input className="form-control" type="date" value={form.startDate}
          onChange={(e) => update('startDate', e.target.value)} />
      </label>
      <label className="form-label">
        Đến ngày (đêm cuối cùng)
        <input className="form-control" type="date" value={form.endDate}
          onChange={(e) => update('endDate', e.target.value)} />
      </label>
      <label className="form-label">
        Giá một đêm (VND)
        <input className="form-control" inputMode="numeric" value={form.pricePerNight} placeholder="900000"
          onChange={(e) => update('pricePerNight', e.target.value)} />
      </label>
      {!initial && roomTypes.length > 0 && (
        <section className="price-holiday-samples" aria-label="Mẫu giá dịp lễ">
          <div className="price-holiday-samples-heading">
            <h3>Mẫu giá lễ 30/4 – 1/5</h3>
            <p>Giá tham khảo được tính bằng giá cuối tuần cộng 20%. Có thể chỉnh trước khi lưu.</p>
          </div>
          <div className="price-holiday-samples-list">
            {roomTypes.map((roomType) => {
              const samplePrice = getHolidaySamplePrice(roomType)
              return (
                <div className="price-holiday-sample" key={roomType.id}>
                  <span>{roomType.name}</span>
                  <strong>{samplePrice === null ? 'Chưa có giá nền' : `${money.format(samplePrice)} đ`}</strong>
                  <button type="button" className="secondary-button" disabled={samplePrice === null}
                    onClick={() => handleUseHolidaySample(roomType)}>
                    Dùng mẫu
                  </button>
                </div>
              )
            })}
          </div>
        </section>
      )}
      {price !== null && price > 0 && (
        <p className="checkin-note">= {money.format(price)} đ / đêm</p>
      )}
      <PriceOverridePreview
        roomTypeId={form.roomTypeId}
        startDate={form.startDate}
        endDate={form.endDate}
        pricePerNight={price}
        excludeId={initial?.id}
      />
      <button className="primary-button" type="submit" disabled={isSaving}>
        {isSaving ? 'Đang lưu...' : initial ? 'Lưu thay đổi' : 'Thêm giá đè'}
      </button>
      {onCancel && (
        <button className="secondary-button" type="button" onClick={onCancel}>
          Huỷ
        </button>
      )}
      <p className="checkin-note">
        Khoảng ngày tính theo đêm, gồm cả hai đầu. Ví dụ 29/04 → 01/05 là 3 đêm.
      </p>
    </form>
  )
}