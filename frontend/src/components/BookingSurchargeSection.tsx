import { useEffect, useState, type FormEvent } from 'react'
import { addBookingSurcharge, getBookingSurcharges } from '../services/bookingSurchargeService'
import type { BookingSurchargeList, SurchargeType } from '../types/bookingSurcharge'
import './BookingSurchargeSection.css'

const TYPE_LABELS: Record<SurchargeType, string> = {
  THEM_NGUOI: 'Thêm người',
  THEM_GIUONG: 'Thêm giường',
}

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })
const MAX_UNIT_PRICE = 100_000_000
type BookingSurchargeSectionProps = {
  bookingId: number
  canAdd: boolean
}

/** S4-02 Lát 1: khu "Phụ thu" trong chi tiết booking. */
export function BookingSurchargeSection({ bookingId, canAdd }: BookingSurchargeSectionProps) {
  const [data, setData] = useState<BookingSurchargeList | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [type, setType] = useState<SurchargeType>('THEM_NGUOI')
  const [quantity, setQuantity] = useState('1')
  const [nights, setNights] = useState('1')
  const [note, setNote] = useState('')
  const [priceInput, setPriceInput] = useState<string | null>(null)
  const [priceReason, setPriceReason] = useState('')
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getBookingSurcharges(bookingId)
      .then((result) => {
        if (!cancelled) setData(result)
      })
      .catch((err) => {
        if (!cancelled) setLoadError(err instanceof Error ? err.message : 'Không tải được phụ thu.')
      })
    return () => {
      cancelled = true
    }
  }, [bookingId])

  const defaultPrice = data ? (type === 'THEM_NGUOI' ? data.defaultExtraPersonPrice : data.defaultExtraBedPrice) : 0
  // S4-02 Lát 4: đơn giá điền sẵn giá mặc định, lễ tân sửa tay được nhưng phải nhập lý do.
  const unitPrice = priceInput === null ? defaultPrice : Number(priceInput)
  const priceValid = priceInput !== '' && Number.isInteger(unitPrice) && unitPrice >= 0 && unitPrice <= MAX_UNIT_PRICE
  const priceChanged = priceInput !== null && unitPrice !== defaultPrice
  const quantityValue = Number(quantity)
  const nightsValue = Number(nights)
  const previewValid = Number.isInteger(quantityValue) && quantityValue >= 1 && Number.isInteger(nightsValue) && nightsValue >= 1
  // S4-02 Lát 3: số người thêm không vượt số chỗ còn lại của loại phòng.
  const isExtraPerson = type === 'THEM_NGUOI'
  const remainingCapacity = data?.remainingCapacity ?? null
  const quantityMax = isExtraPerson && remainingCapacity !== null ? Math.min(10, remainingCapacity) : 10
  const roomFull = isExtraPerson && remainingCapacity !== null && remainingCapacity <= 0
  function resetForm() {
    setShowForm(false)
    setType('THEM_NGUOI')
    setQuantity('1')
    setNights('1')
    setNote('')
    setPriceInput(null)
    setPriceReason('')
    setFormError(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!data) return
    if (!Number.isInteger(quantityValue) || quantityValue < 1) {
      setFormError('Số lượng phải từ 1 trở lên.')
      return
    }
      if (quantityValue > quantityMax) {
      setFormError(isExtraPerson && remainingCapacity !== null
        ? (roomFull
          ? `Phòng đã đủ sức chứa tối đa ${data.maxCapacity} khách, không thể thêm người.`
          : `Chỉ thêm được tối đa ${quantityMax} người (đang có ${data.currentGuestCount} khách / tối đa ${data.maxCapacity}).`)
        : 'Số lượng tối đa là 10.')
      return
    }
    if (!Number.isInteger(nightsValue) || nightsValue < 1 || nightsValue > data.maxNights) {
      setFormError(`Số đêm áp dụng phải từ 1 đến ${data.maxNights}.`)
      return
    }
    if (!priceValid) {
      setFormError('Đơn giá phải là số nguyên từ 0 đến 100.000.000 đ.')
      return
    }
    if (priceChanged && !priceReason.trim()) {
      setFormError('Đơn giá khác giá mặc định, vui lòng nhập lý do sửa giá.')
      return
    }
    setSaving(true)
    setFormError(null)
    try {
      const result = await addBookingSurcharge(bookingId, {
        type,
        quantity: quantityValue,
        nights: nightsValue,
        note: note.trim() || null,
        unitPrice: priceChanged ? unitPrice : null,
        priceReason: priceChanged ? priceReason.trim() : null,
      })
      setData(result)
      resetForm()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Không thêm được phụ thu.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="booking-detail-section booking-detail-full booking-surcharge-section" aria-labelledby="booking-surcharge-title">
      <div className="booking-surcharge-header">
        <h4 id="booking-surcharge-title">Phụ thu</h4>
        {canAdd && data?.canAddSurcharge && !showForm && (
          <button type="button" className="booking-surcharge-add" onClick={() => setShowForm(true)}>
            + Thêm phụ thu
          </button>
        )}
      </div>
      {data && data.maxCapacity !== null && (
        <p className="booking-surcharge-muted">Số khách: {data.currentGuestCount} / tối đa {data.maxCapacity}</p>
      )}
      {loadError && <p className="booking-surcharge-error" role="alert">{loadError}</p>}
      {!data && !loadError && <p className="booking-surcharge-muted">Đang tải phụ thu...</p>}

      {data && (
        <>
          <div className="booking-surcharge-auto" aria-label="Phụ thu thêm người đã chốt">
            <strong>Phụ thu thêm người đã chốt</strong>
            {data.automaticSurchargeBreakdownComplete ? (
              <span>
                {money.format(data.automaticSurchargeAmount)}
                {data.automaticSurchargeAmount > 0 && (
                  <small> ({data.automaticExtraGuestCount} khách × {money.format(data.automaticExtraPersonFeeSnapshot)} / khách / đêm × {data.maxNights} đêm; đã gồm trong tổng booking)</small>
                )}
              </span>
            ) : (
              <small>{data.automaticSurchargeBreakdownWarning ?? 'Không đủ snapshot để xác thực phụ thu đã chốt.'}</small>
            )}
          </div>
          {data.items.length > 0 && <h5>Phụ thu phát sinh</h5>}
          {data.items.length > 0 && (
            <div className="booking-surcharge-table-wrap">
              <table className="booking-surcharge-table">
                <thead>
                  <tr>
                    <th>Loại</th>
                    <th>Số lượng</th>
                    <th>Đơn giá</th>
                    <th>Số đêm</th>
                    <th>Thành tiền</th>
                    <th>Ghi chú</th>
                    <th>Người thêm</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((item) => (
                    <tr key={item.id}>
                      <td>{TYPE_LABELS[item.type] ?? item.type}</td>
                      <td>{item.quantity}</td>
                      <td>
                        {money.format(item.unitPrice)}
                        {item.priceOverridden && (
                          <small className="booking-surcharge-overridden">
                            Giá đã sửa (mặc định {money.format(item.defaultUnitPrice)}): {item.priceOverrideReason}
                          </small>
                        )}
                      </td>
                      <td>{item.nights}</td>
                      <td>{money.format(item.amount)}</td>
                      <td>{item.note || '—'}</td>
                      <td>
                        {item.createdBy}
                        <small>{new Date(item.createdAt).toLocaleString('vi-VN')}</small>
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <th colSpan={4}>Tổng phụ thu</th>
                    <td colSpan={3}><strong>{money.format(data.totalAmount)}</strong></td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}

          {data.items.length === 0 && data.automaticSurchargeAmount <= 0 && (
            <p className="booking-surcharge-muted">Chưa có khoản phụ thu nào.</p>
          )}

          {!data.canAddSurcharge && (
            <p className="booking-surcharge-muted">Chỉ thêm phụ thu cho booking đã xác nhận hoặc đang ở.</p>
          )}

          {showForm && (
            <form className="booking-surcharge-form" onSubmit={(event) => void handleSubmit(event)} noValidate>
              <label>
                Loại phụ thu
                <select value={type} onChange={(e) => { setType(e.target.value as SurchargeType); setPriceInput(null) }}>
                  <option value="THEM_NGUOI">Thêm người</option>
                  <option value="THEM_GIUONG">Thêm giường</option>
                </select>
              </label>
              <label>
                Đơn giá (mặc định {money.format(defaultPrice)})
                <input type="number" min={0} max={MAX_UNIT_PRICE} step={1000}
                  value={priceInput ?? String(defaultPrice)}
                  onChange={(e) => setPriceInput(e.target.value)} />
              </label>
              {priceChanged && (
                <label className="booking-surcharge-wide">
                  <span>Lý do sửa giá <span className="booking-surcharge-required">*</span></span>
                  <input maxLength={500} value={priceReason} onChange={(e) => setPriceReason(e.target.value)}
                    placeholder="Bắt buộc khi đơn giá khác giá mặc định" />
                </label>
              )}
              {isExtraPerson && data.maxCapacity !== null && (
                <p className={`booking-surcharge-capacity booking-surcharge-wide${roomFull ? ' is-full' : ''}`}>
                  Đang có {data.currentGuestCount} khách / tối đa {data.maxCapacity}
                  {roomFull ? ' · đã đủ sức chứa, không thể thêm người' : ` · còn ${remainingCapacity} chỗ`}
                </p>
              )}
              <label>
                Số lượng
                <input type="number" min={1} max={Math.max(1, quantityMax)} value={quantity} onChange={(e) => setQuantity(e.target.value)} disabled={roomFull} />
              </label>
              <label>
                Số đêm áp dụng (tối đa {data.maxNights})
                <input type="number" min={1} max={data.maxNights} value={nights} onChange={(e) => setNights(e.target.value)} />
              </label>
              <label className="booking-surcharge-wide">
                Ghi chú
                <input maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Không bắt buộc" />
              </label>
              <p className="booking-surcharge-preview">
                Thành tiền: <strong>{previewValid && priceValid ? money.format(quantityValue * unitPrice * nightsValue) : '—'}</strong>
              </p>
              {formError && <p className="booking-surcharge-error" role="alert">{formError}</p>}
              <div className="booking-surcharge-actions">
                <button type="button" onClick={resetForm} disabled={saving}>Huỷ</button>
                <button type="submit" className="booking-surcharge-add" disabled={saving || roomFull}>
                  {saving ? 'Đang lưu...' : 'Lưu phụ thu'}
                </button>
              </div>
            </form>
          )}
        </>
      )}
    </section>
  )
}
