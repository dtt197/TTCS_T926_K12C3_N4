import { useEffect, useMemo, useState } from 'react'
import { previewPriceOverride } from '../services/priceOverrideService'
import type { PriceOverridePreview as PreviewData, PriceOverridePreviewPayload } from '../types/priceOverride'

type PriceOverridePreviewProps = {
  roomTypeId: string
  startDate: string
  endDate: string
  pricePerNight: number | null
  excludeId?: number
}

/** Kết quả gắn với key của dữ liệu đã gửi, để không hiện nhầm kết quả của lần nhập trước. */
type PreviewResult = { key: string; data?: PreviewData; error?: string }

const money = new Intl.NumberFormat('vi-VN')
const WEEKDAYS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7']

/** yyyy-MM-dd thành "T6 30/04". */
function formatNight(isoDate: string) {
  const [year, month, day] = isoDate.split('-').map(Number)
  const weekday = WEEKDAYS[new Date(year, month - 1, day).getDay()]
  return `${weekday} ${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}`
}

function formatMoney(value: number | null, empty: string) {
  return value === null ? empty : `${money.format(value)} đ`
}

/** S2-02 Lát 4 (AC4): bảng giá từng đêm của đợt đang nhập, tự cập nhật khi đổi ngày, loại phòng hoặc giá. */
export function PriceOverridePreview({ roomTypeId, startDate, endDate, pricePerNight, excludeId }: PriceOverridePreviewProps) {
  const payload = useMemo<PriceOverridePreviewPayload | null>(() => {
    // Ngày dạng yyyy-MM-dd nên so sánh chuỗi là đúng thứ tự thời gian.
    if (!roomTypeId || !startDate || !endDate || endDate < startDate) return null
    return {
      roomTypeId: Number(roomTypeId),
      startDate,
      endDate,
      pricePerNight: pricePerNight !== null && pricePerNight > 0 ? pricePerNight : null,
      excludeId: excludeId ?? null,
    }
  }, [roomTypeId, startDate, endDate, pricePerNight, excludeId])
  const key = payload ? JSON.stringify(payload) : null
  const [result, setResult] = useState<PreviewResult | null>(null)

  useEffect(() => {
    if (!payload || !key) return
    let cancelled = false
    // Đợi người dùng ngừng gõ 400ms rồi mới gọi API, tránh gọi liên tục khi đang nhập giá.
    const timer = window.setTimeout(() => {
      previewPriceOverride(payload)
        .then((data) => {
          if (!cancelled) setResult({ key, data })
        })
        .catch((err: unknown) => {
          if (!cancelled) {
            setResult({ key, error: err instanceof Error ? err.message : 'Không xem trước được giá' })
          }
        })
    }, 400)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [payload, key])

  if (!payload) {
    return <p className="price-preview-hint">Chọn loại phòng và khoảng ngày để xem trước giá từng đêm.</p>
  }

  const shown = result?.key === key ? result : null

  return (
    <section className="price-preview" aria-live="polite">
      <h3>Xem trước giá từng đêm</h3>
      {!shown ? (
        <p className="price-preview-hint">Đang tính giá...</p>
      ) : shown.error ? (
        <div className="price-notice error">{shown.error}</div>
      ) : shown.data ? (
        <>
          {shown.data.conflict && <div className="price-notice error">{shown.data.conflict}</div>}
          <div className="price-preview-table-wrapper">
            <table className="price-preview-table">
              <thead>
                <tr>
                  <th>Đêm</th>
                  <th>Giá hiện tại</th>
                  <th>Sau khi lưu</th>
                </tr>
              </thead>
              <tbody>
                {shown.data.nightlyPrices.map((night) => (
                  <tr key={night.date}>
                    <td>{formatNight(night.date)}</td>
                    <td>
                      {formatMoney(night.currentPrice, 'Chưa khai báo')}
                      <small>{night.currentLabel}</small>
                    </td>
                    <td className="price-money">{formatMoney(night.newPrice, '—')}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td>{shown.data.nights} đêm</td>
                  <td>{formatMoney(shown.data.currentTotal, '—')}</td>
                  <td className="price-money">{formatMoney(shown.data.newTotal, '—')}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </>
      ) : null}
    </section>
  )
}