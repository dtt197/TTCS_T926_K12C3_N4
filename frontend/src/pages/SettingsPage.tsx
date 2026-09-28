import { useCallback, useEffect, useState } from 'react'
import { SettingsForm } from '../components/SettingsForm'
import { hasPermission } from '../permissions/rolePermissions'
import { getSettings, getSettingsHistory, updateSettings } from '../services/settingsService'
import type { CancellationTier, OperatingSettings, OperatingSettingsPayload } from '../types/settings'
import '../App.css'
import './UserManagementPage.css'

type SettingsPageProps = {
  role: string
}

const money = new Intl.NumberFormat('vi-VN')

/** Tiền VND có dấu phân cách nghìn, ví dụ 100.000 đ. */
function formatMoney(value: number) {
  return `${money.format(value)} đ`
}

/** Ngày giờ dd/MM/yyyy HH:mm theo múi giờ Asia/Ho_Chi_Minh (yêu cầu phi chức năng trong Excel). */
function formatDateTime(iso: string) {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(iso)).replace(',', '')
}

function formatTiers(tiers: CancellationTier[]) {
  return tiers.map((tier) => `≥ ${tier.hoursBeforeCheckIn} giờ: hoàn ${tier.refundPercent}%`).join(' · ')
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

  const load = useCallback(() =>
    Promise.all([getSettings(), canViewHistory ? getSettingsHistory() : Promise.resolve([])])
      .then(([settings, versions]) => {
        setCurrent(settings)
        setHistory(versions)
        setLoadError(null)
      })
      .catch((err: unknown) => {
        setLoadError(err instanceof Error ? err.message : 'Không tải được tham số vận hành')
      })
      .finally(() => {
        setIsLoading(false)
      }),
  [canViewHistory])

  useEffect(() => {
    void load()
  }, [load])

  async function handleSave(payload: OperatingSettingsPayload) {
    const saved = await updateSettings(payload)
    setNotice(`Đã lưu tham số lúc ${formatDateTime(saved.updatedAt)}. Chỉ áp cho booking tạo từ bây giờ.`)
    await load()
  }

  return (
    <div className="app-shell">
      <main className="main-content">
        <section className="intro">
          <div>
            <p className="eyebrow">Vận hành · S1-09</p>
            <h1>Tham số vận hành</h1>
            <p>Giờ nhận – trả phòng, phụ thu và chính sách huỷ áp thống nhất cho mọi lễ tân.</p>
          </div>
        </section>

        {isLoading ? (
          <p className="empty-state">Đang tải...</p>
        ) : loadError || !current ? (
          <div className="alert" role="alert">{loadError ?? 'Chưa khai báo tham số vận hành'}</div>
        ) : (
          <section className="workspace-grid">
            <div className="panel">
              <div className="panel-heading">
                <div>
                  <h2>Đang áp dụng</h2>
                  <p>
                    Sửa lần cuối bởi {current.updatedByName} lúc {formatDateTime(current.updatedAt)}
                  </p>
                </div>
              </div>

              {notice && <div className="alert success" role="status">{notice}</div>}

              <div className="user-table-wrapper">
                <table className="user-table">
                  <tbody>
                    <tr><th>Homestay</th><td>{current.homestayName}</td></tr>
                    <tr><th>Địa chỉ</th><td>{current.address ?? '—'}</td></tr>
                    <tr><th>Điện thoại</th><td>{current.phone ?? '—'}</td></tr>
                    <tr><th>Email</th><td>{current.email ?? '—'}</td></tr>
                    <tr><th>Giờ nhận phòng</th><td>{current.checkInTime.slice(0, 5)}</td></tr>
                    <tr><th>Giờ trả phòng</th><td>{current.checkOutTime.slice(0, 5)}</td></tr>
                    <tr><th>Phụ thu trả muộn</th><td>{formatMoney(current.lateCheckoutFeePerHour)} / giờ</td></tr>
                    <tr><th>Phụ thu thêm người</th><td>{formatMoney(current.extraPersonFee)} / người / đêm</td></tr>
                    <tr><th>Chính sách huỷ</th><td>{formatTiers(current.cancellationTiers)}</td></tr>
                  </tbody>
                </table>
              </div>

              {canViewHistory && (
                <>
                  <h2>Lịch sử thay đổi</h2>
                  <div className="user-table-wrapper">
                    <table className="user-table">
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
                            <td>{version.checkInTime.slice(0, 5)} / {version.checkOutTime.slice(0, 5)}</td>
                            <td>
                              {formatMoney(version.lateCheckoutFeePerHour)}/giờ
                              <small>{formatMoney(version.extraPersonFee)}/người</small>
                            </td>
                            <td>{formatTiers(version.cancellationTiers)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </div>

            {canManage && (
              <aside className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>Sửa tham số</h2>
                    <p>Chỉ Chủ homestay thực hiện được.</p>
                  </div>
                </div>
                <SettingsForm key={current.id} initial={current} onSubmit={handleSave} />
              </aside>
            )}
          </section>
        )}
      </main>
    </div>
  )
}