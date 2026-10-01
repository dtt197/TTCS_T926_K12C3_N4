import { useCallback, useEffect, useState } from 'react'
import { PriceOverrideForm } from '../components/PriceOverrideForm'
import { hasPermission } from '../permissions/rolePermissions'
import {
  createPriceOverride,
  deletePriceOverride,
  getPriceOverrides,
  updatePriceOverride,
} from '../services/priceOverrideService'
import { getRoomTypes } from '../services/roomTypeService'
import type { PriceOverride, PriceOverridePayload } from '../types/priceOverride'
import type { RoomType } from '../types/roomType'
import '../App.css'
import './PriceOverridePage.css'

type PriceOverridePageProps = {
  role: string
}

type Notice = { type: 'success' | 'error'; text: string }

const money = new Intl.NumberFormat('vi-VN')

/** yyyy-MM-dd thành dd/MM/yyyy (yêu cầu định dạng ngày trong Excel). */
function formatDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

/** S2-02 Lát 1: Chủ homestay thêm/sửa/xoá giá đè; Quản trị và Lễ tân chỉ xem. */
export function PriceOverridePage({ role }: PriceOverridePageProps) {
  const canManage = hasPermission(role, 'pricing:manage')
  const [priceOverrides, setPriceOverrides] = useState<PriceOverride[]>([])
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [editing, setEditing] = useState<PriceOverride | null>(null)

  const load = useCallback(
    () =>
      Promise.all([getPriceOverrides(), canManage ? getRoomTypes() : Promise.resolve([])])
        .then(([items, types]) => {
          setPriceOverrides(items)
          setRoomTypes(types)
          setLoadError(null)
        })
        .catch((err: unknown) => {
          setLoadError(err instanceof Error ? err.message : 'Không tải được danh sách giá đè')
        })
        .finally(() => {
          setIsLoading(false)
        }),
    [canManage],
  )

  useEffect(() => {
    void load()
  }, [load])

  async function handleCreate(payload: PriceOverridePayload) {
    const created = await createPriceOverride(payload)
    setNotice({ type: 'success', text: `Đã thêm giá đè "${created.name}" cho ${created.roomTypeName}` })
    await load()
  }

  async function handleUpdate(payload: PriceOverridePayload) {
    if (!editing) return
    const updated = await updatePriceOverride(editing.id, payload)
    setEditing(null)
    setNotice({ type: 'success', text: `Đã lưu giá đè "${updated.name}"` })
    await load()
  }

  async function handleDelete(priceOverride: PriceOverride) {
    if (!window.confirm(`Xoá giá đè "${priceOverride.name}" của ${priceOverride.roomTypeName}?`)) return
    try {
      await deletePriceOverride(priceOverride.id)
      if (editing?.id === priceOverride.id) setEditing(null)
      setNotice({ type: 'success', text: `Đã xoá giá đè "${priceOverride.name}"` })
      await load()
    } catch (err) {
      setNotice({ type: 'error', text: err instanceof Error ? err.message : 'Không xoá được giá đè' })
    }
  }

  return (
    <div className="price-page">
      {notice && (
        <div className={`price-notice ${notice.type}`} role="status">
          {notice.text}
        </div>
      )}

      <section className={canManage ? 'price-workspace' : 'price-workspace readonly'}>
        <div className="price-panel">
          <div className="price-section-heading">
            <div>
              <span className="price-kicker">DANH SÁCH</span>
              <h2>Các đợt giá đè</h2>
              <p>Giá đè áp cho từng loại phòng trong khoảng ngày của đợt (mùa cao điểm, ngày lễ).</p>
            </div>
            <span className="price-count">{priceOverrides.length} đợt</span>
          </div>

          {isLoading ? (
            <div className="price-empty">Đang tải...</div>
          ) : loadError ? (
            <div className="price-notice error" role="alert">{loadError}</div>
          ) : priceOverrides.length === 0 ? (
            <div className="price-empty">Chưa có đợt giá đè nào.</div>
          ) : (
            <div className="price-table-wrapper">
              <table className="price-table">
                <thead>
                  <tr>
                    <th>Tên đợt</th>
                    <th>Loại phòng</th>
                    <th>Khoảng ngày</th>
                    <th>Số đêm</th>
                    <th>Giá một đêm</th>
                    {canManage && <th>Thao tác</th>}
                  </tr>
                </thead>
                <tbody>
                  {priceOverrides.map((priceOverride) => (
                    <tr key={priceOverride.id}>
                      <td>{priceOverride.name}</td>
                      <td>
                        {priceOverride.roomTypeName}
                        <small>{priceOverride.roomTypeCode}</small>
                      </td>
                      <td>
                        {formatDate(priceOverride.startDate)} – {formatDate(priceOverride.endDate)}
                      </td>
                      <td>{priceOverride.nights}</td>
                      <td className="price-money">{money.format(priceOverride.pricePerNight)} đ</td>
                      {canManage && (
                        <td>
                          <div className="price-actions">
                            <button className="secondary-button" type="button"
                              onClick={() => setEditing(priceOverride)}>
                              Sửa
                            </button>
                            <button className="secondary-button danger" type="button"
                              onClick={() => handleDelete(priceOverride)}>
                              Xoá
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {canManage && (
          <aside className="price-panel price-form-panel">
            <div className="price-section-heading compact">
              <div>
                <span className="price-kicker">{editing ? 'CHỈNH SỬA' : 'THÊM MỚI'}</span>
                <h2>{editing ? `Sửa "${editing.name}"` : 'Thêm giá đè'}</h2>
              </div>
            </div>
            <PriceOverrideForm
              key={editing?.id ?? 'create'}
              initial={editing ?? undefined}
              roomTypes={roomTypes}
              onSubmit={editing ? handleUpdate : handleCreate}
              onCancel={editing ? () => setEditing(null) : undefined}
            />
          </aside>
        )}
      </section>
    </div>
  )
}