import { useCallback, useEffect, useMemo, useState } from 'react'
import { SettingsForm } from '../components/SettingsForm'
import { hasPermission } from '../permissions/rolePermissions'
import { getSettings, getSettingsHistory, updateSettings } from '../services/settingsService'
import type { CancellationTier, OperatingSettings, OperatingSettingsPayload } from '../types/settings'
import '../App.css'
import './SettingsPage.css'

type SettingsPageProps = {
  role: string
}

const money = new Intl.NumberFormat('vi-VN')

/** Tiền VND có dấu phân cách nghìn, ví dụ 100.000 đ. */
function formatMoney(value: number) {
  return `${money.format(value)} đ`
}

/** Ngày giờ dd/MM/yyyy HH:mm theo múi giờ Asia/Ho_Chi_Minh. */
function formatDateTime(iso: string) {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
    .format(new Date(iso))
    .replace(',', '')
}

function formatTiers(tiers: CancellationTier[]) {
  return tiers
    .map(
      (tier) =>
        `≥ ${tier.hoursBeforeCheckIn} giờ: hoàn ${tier.refundPercent}%`,
    )
    .join(' · ')
}

/** S1-09: Chủ homestay sửa tham số; Quản trị và Lễ tân chỉ xem. */
export function SettingsPage({ role }: SettingsPageProps) {
  const canManage = hasPermission(role, 'settings:manage')
  const canViewHistory = hasPermission(role, 'settings:history')
  const [current, setCurrent] = useState<OperatingSettings | null>(null)
  const [history, setHistory] = useState<OperatingSettings[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(
    () =>
      Promise.all([
        getSettings(),
        canViewHistory ? getSettingsHistory() : Promise.resolve([]),
      ])
        .then(([settings, versions]) => {
          setCurrent(settings)
          setHistory(versions)
          setLoadError(null)
        })
        .catch((err: unknown) => {
          setLoadError(
            err instanceof Error
              ? err.message
              : 'Không tải được tham số vận hành',
          )
        })
        .finally(() => {
          setIsLoading(false)
        }),
    [canViewHistory],
  )

  useEffect(() => {
    void load()
  }, [load])

  const stats = useMemo(() => {
    if (!current) {
      return {
        checkIn: '--:--',
        checkOut: '--:--',
        tierCount: 0,
      }
    }

    return {
      checkIn: current.checkInTime.slice(0, 5),
      checkOut: current.checkOutTime.slice(0, 5),
      tierCount: current.cancellationTiers.length,
    }
  }, [current])

  async function handleSave(payload: OperatingSettingsPayload) {
    const saved = await updateSettings(payload)

    setNotice(
      `Đã lưu tham số lúc ${formatDateTime(
        saved.updatedAt,
      )}. Chỉ áp cho booking tạo từ bây giờ.`,
    )

    await load()
  }

  return (
    <div className="settings-page">
     
      <section className="settings-summary" aria-label="Tổng quan vận hành">
        <article className="settings-summary-card">
          <span className="settings-summary-icon">↘</span>
          <div>
            <span>Giờ nhận phòng</span>
            <strong>{stats.checkIn}</strong>
          </div>
        </article>

        <article className="settings-summary-card">
          <span className="settings-summary-icon">↗</span>
          <div>
            <span>Giờ trả phòng</span>
            <strong>{stats.checkOut}</strong>
          </div>
        </article>

        <article className="settings-summary-card">
          <span className="settings-summary-icon">%</span>
          <div>
            <span>Mốc chính sách huỷ</span>
            <strong>{stats.tierCount}</strong>
          </div>
        </article>
      </section>

      {isLoading ? (
        <div className="settings-empty">Đang tải...</div>
      ) : loadError || !current ? (
        <div className="settings-notice error" role="alert">
          {loadError ?? 'Chưa khai báo tham số vận hành'}
        </div>
      ) : (
        <>
          {notice && (
            <div className="settings-notice success" role="status">
              {notice}
            </div>
          )}

          <section
            className={
              canManage
                ? 'settings-workspace'
                : 'settings-workspace readonly'
            }
          >
            <div className="settings-main-column">
              <section className="settings-current-panel">
                <div className="settings-section-heading">
                  <div>
                    <span className="settings-kicker">ĐANG ÁP DỤNG</span>
                    <h2>Thông tin vận hành hiện tại</h2>
                    <p>
                      Sửa lần cuối bởi <strong>{current.updatedByName}</strong>{' '}
                      lúc {formatDateTime(current.updatedAt)}
                    </p>
                  </div>
                </div>

                <div className="settings-info-grid">
                  <article className="settings-info-card wide">
                    <span className="settings-info-label">Homestay</span>
                    <strong>{current.homestayName}</strong>
                    <p>{current.address ?? 'Chưa khai báo địa chỉ'}</p>
                  </article>

                  <article className="settings-info-card">
                    <span className="settings-info-label">Điện thoại</span>
                    <strong>{current.phone ?? '—'}</strong>
                  </article>

                  <article className="settings-info-card">
                    <span className="settings-info-label">Email</span>
                    <strong>{current.email ?? '—'}</strong>
                  </article>

                  <article className="settings-info-card accent">
                    <span className="settings-info-label">Nhận phòng</span>
                    <strong>{current.checkInTime.slice(0, 5)}</strong>
                  </article>

                  <article className="settings-info-card accent">
                    <span className="settings-info-label">Trả phòng</span>
                    <strong>{current.checkOutTime.slice(0, 5)}</strong>
                  </article>

                  <article className="settings-info-card">
                    <span className="settings-info-label">
                      Phụ thu trả muộn
                    </span>
                    <strong>
                      {formatMoney(current.lateCheckoutFeePerHour)}
                    </strong>
                    <p>/ giờ</p>
                  </article>

                  <article className="settings-info-card">
                    <span className="settings-info-label">
                      Phụ thu thêm người
                    </span>
                    <strong>{formatMoney(current.extraPersonFee)}</strong>
                    <p>/ người / đêm</p>
                  </article>
                </div>

                <div className="settings-cancellation">
                  <div className="settings-cancellation-heading">
                    <div>
                      <span className="settings-info-label">
                        Chính sách huỷ
                      </span>
                      <h3>Các mốc hoàn cọc đang áp dụng</h3>
                    </div>
                    <span className="settings-tier-count">
                      {current.cancellationTiers.length} mốc
                    </span>
                  </div>

                  <div className="settings-tier-list">
                    {current.cancellationTiers.map((tier, index) => (
                      <article
                        className="settings-tier-card"
                        key={`${tier.hoursBeforeCheckIn}-${tier.refundPercent}-${index}`}
                      >
                        <span className="settings-tier-index">
                          {index + 1}
                        </span>
                        <div>
                          <strong>
                            Trước {tier.hoursBeforeCheckIn} giờ
                          </strong>
                          <p>
                            Hoàn {tier.refundPercent}% tiền cọc
                          </p>
                        </div>
                      </article>
                    ))}
                  </div>
                </div>
              </section>

              {canViewHistory && (
                <section className="settings-history-panel">
                  <div className="settings-section-heading">
                    <div>
                      <span className="settings-kicker">LỊCH SỬ</span>
                      <h2>Lịch sử thay đổi</h2>
                      <p>
                        Mỗi lần lưu tạo một phiên bản mới và chỉ áp cho booking
                        phát sinh sau thời điểm lưu.
                      </p>
                    </div>
                    <span className="settings-history-count">
                      {history.length} phiên bản
                    </span>
                  </div>

                  {history.length === 0 ? (
                    <div className="settings-empty compact">
                      Chưa có lịch sử thay đổi.
                    </div>
                  ) : (
                    <div className="settings-history-wrapper">
                      <table className="settings-history-table">
                        <thead>
                          <tr>
                            <th>Thời điểm</th>
                            <th>Người sửa</th>
                            <th>Nhận / trả</th>
                            <th>Phụ thu</th>
                            <th>Chính sách huỷ</th>
                          </tr>
                        </thead>
                        <tbody>
                          {history.map((version) => (
                            <tr key={version.id}>
                              <td>{formatDateTime(version.updatedAt)}</td>
                              <td>{version.updatedByName}</td>
                              <td>
                                <span className="settings-time-chip">
                                  {version.checkInTime.slice(0, 5)}
                                </span>
                                <span className="settings-time-divider">/</span>
                                <span className="settings-time-chip">
                                  {version.checkOutTime.slice(0, 5)}
                                </span>
                              </td>
                              <td>
                                <strong>
                                  {formatMoney(
                                    version.lateCheckoutFeePerHour,
                                  )}
                                  /giờ
                                </strong>
                                <small>
                                  {formatMoney(version.extraPersonFee)}/người
                                </small>
                              </td>
                              <td>
                                <span className="settings-history-policy">
                                  {formatTiers(version.cancellationTiers)}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </section>
              )}
            </div>

            {canManage && (
              <aside className="settings-form-panel">
                <div className="settings-section-heading compact">
                  <div>
                    <span className="settings-kicker">CHỈNH SỬA</span>
                    <h2>Sửa tham số</h2>
                    <p>Chỉ Chủ homestay thực hiện được.</p>
                  </div>
                </div>

                <SettingsForm
                  key={current.id}
                  initial={current}
                  onSubmit={handleSave}
                />
              </aside>
            )}
          </section>
        </>
      )}
    </div>
  )
}
