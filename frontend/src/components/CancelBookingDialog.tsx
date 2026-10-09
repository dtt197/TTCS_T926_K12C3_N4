import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiRequestError } from '../services/apiClient'
import {
  cancelBooking,
  getCancellationPreview,
} from '../services/bookingCancellationService'
import type {
  BookingCancellationResult,
  CancelReason,
  CancellationPreview,
} from '../types/booking'
import './CancelBookingDialog.css'

const REASON_OPTIONS: { value: CancelReason; label: string }[] = [
  { value: 'KHACH_DOI_KE_HOACH', label: 'Khách đổi kế hoạch' },
  { value: 'KHACH_KHONG_LIEN_LAC', label: 'Khách không liên lạc được' },
  { value: 'TRUNG_BOOKING', label: 'Trùng booking' },
  { value: 'LY_DO_KHAC', label: 'Lý do khác' },
]

const money = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
})

function formatDate(isoDate: string): string {
  return isoDate.split('-').reverse().join('/')
}

function describeError(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 400) {
      return 'Vui lòng chọn lý do huỷ (và nhập ghi chú nếu chọn "Lý do khác").'
    }
    if (error.status === 403) {
      return 'Bạn không có quyền huỷ booking này.'
    }
    if (error.status === 404) {
      return 'Không tìm thấy booking.'
    }
    if (error.status === 409) {
      return 'Booking đã huỷ hoặc không còn huỷ được (ví dụ đã nhận phòng).'
    }
  }
  return 'Có lỗi xảy ra, vui lòng thử lại.'
}

type CancelBookingDialogProps = {
  bookingCode: string
  onClose: () => void
  onCancelled: (result: BookingCancellationResult) => void
}

/** S3-05: hộp thoại huỷ booking. Số tiền hoàn chỉ hiển thị, không có ô nhập tay. */
export default function CancelBookingDialog({
  bookingCode,
  onClose,
  onCancelled,
}: CancelBookingDialogProps) {
  const [preview, setPreview] = useState<CancellationPreview | null>(null)
  const [loadError, setLoadError] = useState('')
  const [reason, setReason] = useState<CancelReason | ''>('')
  const [note, setNote] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState('')

  useEffect(() => {
    let active = true
    getCancellationPreview(bookingCode)
      .then((data) => {
        if (active) setPreview(data)
      })
      .catch((error: unknown) => {
        if (active) setLoadError(describeError(error))
      })
    return () => {
      active = false
    }
  }, [bookingCode])

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !submitting) onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose, submitting])

  const needsNote = reason === 'LY_DO_KHAC'
  const canSubmit =
    preview !== null &&
    reason !== '' &&
    (!needsNote || note.trim() !== '') &&
    !submitting

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canSubmit || !reason ) return

    setSubmitting(true)
    setSubmitError('')
    try {
      const result = await cancelBooking(bookingCode, {
        reason,
        note: note.trim() === '' ? undefined : note.trim(),
      })
      onCancelled(result)
    } catch (error: unknown) {
      setSubmitError(describeError(error))
      setSubmitting(false)
    }
  }

  function describeTier(data: CancellationPreview): string {
    if (data.hoursBeforeCheckIn < 0) {
      return 'Đã quá giờ nhận phòng, không thuộc mốc hoàn cọc nào'
    }
    if (data.appliedTierHours === null) {
      return 'Không thuộc mốc hoàn cọc nào'
    }
    return `Huỷ trước giờ nhận phòng ít nhất ${data.appliedTierHours} giờ`
  }

  return (
    <div className="cancel-dialog-overlay">
      <div
        className="cancel-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cancel-dialog-title"
      >
        <h2 id="cancel-dialog-title">Huỷ booking {bookingCode}</h2>

        {loadError !== '' && (
          <p className="cancel-dialog-error" role="alert">
            {loadError}
          </p>
        )}

        {loadError === '' && preview === null && <p>Đang tải thông tin huỷ...</p>}

        {preview !== null && (
          <form onSubmit={handleSubmit}>
            <p className="cancel-dialog-guest">
              {preview.guestName} · {preview.roomTypeName}
            </p>
            <p className="cancel-dialog-hint">
              Nhận phòng {formatDate(preview.checkInDate)} lúc{' '}
              {preview.checkInTime.slice(0, 5)}
            </p>

            <label htmlFor="cancel-reason">Lý do huỷ (bắt buộc)</label>
            <select
              id="cancel-reason"
              value={reason}
              onChange={(event) =>
                setReason(event.target.value as CancelReason | '')
              }
              disabled={submitting}
            >
              <option value="">-- Chọn lý do --</option>
              {REASON_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>

            <label htmlFor="cancel-note">
              Ghi chú{needsNote ? ' (bắt buộc)' : ' (không bắt buộc)'}
            </label>
            <textarea
              id="cancel-note"
              value={note}
              maxLength={500}
              rows={3}
              onChange={(event) => setNote(event.target.value)}
              disabled={submitting}
            />

            <dl className="cancel-dialog-refund">
              <div>
                <dt>Tiền cọc đã ghi nhận</dt>
                <dd>{money.format(preview.depositAmount)}</dd>
              </div>
              <div>
                <dt>Mốc áp dụng</dt>
                <dd>{describeTier(preview)}</dd>
              </div>
              <div>
                <dt>Tỷ lệ hoàn cọc</dt>
                <dd>{preview.refundPercent}%</dd>
              </div>
              <div className="cancel-dialog-refund-total">
                <dt>Số tiền hoàn (hệ thống tính)</dt>
                <dd>{money.format(preview.refundAmount)}</dd>
              </div>
            </dl>

            {preview.depositAmount === 0 && (
              <p className="cancel-dialog-hint">
                Booking này chưa có tiền cọc được ghi nhận nên số tiền hoàn là 0.
              </p>
            )}

            {submitError !== '' && (
              <p className="cancel-dialog-error" role="alert">
                {submitError}
              </p>
            )}

            <div className="cancel-dialog-actions">
              <button type="button" onClick={onClose} disabled={submitting}>
                Đóng
              </button>
              <button
                type="submit"
                className="cancel-dialog-confirm"
                disabled={!canSubmit}
              >
                {submitting ? 'Đang huỷ...' : 'Xác nhận huỷ'}
              </button>
            </div>
          </form>
        )}

        {preview === null && (
          <div className="cancel-dialog-actions">
            <button type="button" onClick={onClose}>
              Đóng
            </button>
          </div>
        )}
      </div>
    </div>
  )
}