import { useEffect, useState, type FormEvent } from 'react'
import { addBookingSurcharge, getBookingSurcharges } from '../services/bookingSurchargeService'
import type { BookingSurchargeList, SurchargeType } from '../types/bookingSurcharge'
import './BookingSurchargeSection.css'

const TYPE_LABELS: Record<SurchargeType, string> = {
  THEM_NGUOI: 'Thêm người',
  THEM_GIUONG: 'Thêm giường',
}

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })

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

  const unitPrice = data ? (type === 'THEM_NGUOI' ? data.defaultExtraPersonPrice : data.defaultExtraBedPrice) : 0
  const quantityValue = Number(quantity)
  const nightsValue = Number(nights)
  const previewValid = Number.isInteger(quantityValue) && quantityValue >= 1 && Number.isInteger(nightsValue) && nightsValue >= 1

  function resetForm() {
    setShowForm(false)
    setType('THEM_NGUOI')
    setQuantity('1')
    setNights('1')
    setNote('')
    setFormError(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!data) return
    if (!Number.isInteger(quantityValue) || quantityValue < 1) {
      setFormError('Số lượng phải từ 1 trở lên.')
      return
    }
    if (!Number.isInteger(nightsValue) || nightsValue < 1 || nightsValue > data.maxNights) {
      setFormError(`Số đêm áp dụng phải từ 1 đến ${data.maxNights}.`)
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

      {loadError && <p className="booking-surcharge-error" role="alert">{loadError}</p>}
      {!data && !loadError && <p className="booking-surcharge-muted">Đang tải phụ thu...</p>}

      {data && (
        <>
          {data.items.length === 0 ? (
            <p className="booking-surcharge-muted">Chưa có dòng phụ thu nào.</p>
          ) : (
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
                      <td>{money.format(item.unitPrice)}</td>
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

          {!data.canAddSurcharge && (
            <p className="booking-surcharge-muted">Chỉ thêm phụ thu cho booking đã xác nhận hoặc đang ở.</p>
          )}

          {showForm && (
            <form className="booking-surcharge-form" onSubmit={(event) => void handleSubmit(event)} noValidate>
              <label>
                Loại phụ thu
                <select value={type} onChange={(e) => setType(e.target.value as SurchargeType)}>
                  <option value="THEM_NGUOI">Thêm người</option>
                  <option value="THEM_GIUONG">Thêm giường</option>
                </select>
              </label>
              <label>
                Đơn giá mặc định
                <input value={money.format(unitPrice)} readOnly aria-readonly="true" />
              </label>
              <label>
                Số lượng
                <input type="number" min={1} max={10} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
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
                Thành tiền: <strong>{previewValid ? money.format(quantityValue * unitPrice * nightsValue) : '—'}</strong>
              </p>
              {formError && <p className="booking-surcharge-error" role="alert">{formError}</p>}
              <div className="booking-surcharge-actions">
                <button type="button" onClick={resetForm} disabled={saving}>Huỷ</button>
                <button type="submit" className="booking-surcharge-add" disabled={saving}>
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