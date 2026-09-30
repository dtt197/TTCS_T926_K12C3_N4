import { useCallback, useEffect, useMemo, useState } from 'react'
import { AmenityForm } from '../components/AmenityForm'
import { hasPermission } from '../permissions/rolePermissions'
import {
  createAmenity,
  deleteAmenity,
  getAmenities,
  updateAmenity,
  updateAmenityStatus,
} from '../services/amenityService'
import type { Amenity, AmenityPayload } from '../types/amenity'
import '../App.css'
import './AmenityPage.css'

type AmenityPageProps = {
  role: string
}

type Notice = { type: 'success' | 'error'; text: string }

/** S1-08: Chủ homestay và Quản trị thêm/sửa/ngừng dùng/xoá tiện nghi, Lễ tân chỉ xem. */
export function AmenityPage({ role }: AmenityPageProps) {
  const canManage = hasPermission(role, 'amenities:manage')
  const [amenities, setAmenities] = useState<Amenity[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [editing, setEditing] = useState<Amenity | null>(null)

  const load = useCallback(
    () =>
      getAmenities()
        .then((items) => {
          setAmenities(items)
          setLoadError(null)
        })
        .catch((err: unknown) => {
          setLoadError(
            err instanceof Error
              ? err.message
              : 'Không tải được danh sách tiện nghi',
          )
        })
        .finally(() => {
          setIsLoading(false)
        }),
    [],
  )

  useEffect(() => {
    void load()
  }, [load])

  const stats = useMemo(
    () => ({
      total: amenities.length,
      active: amenities.filter((amenity) => amenity.active).length,
      linked: amenities.filter((amenity) => amenity.roomTypeCount > 0).length,
    }),
    [amenities],
  )

  async function handleCreate(payload: AmenityPayload) {
    const created = await createAmenity(payload)
    setNotice({
      type: 'success',
      text: `Đã thêm tiện nghi ${created.name}`,
    })
    await load()
  }

  async function handleUpdate(payload: AmenityPayload) {
    if (!editing) return

    const updated = await updateAmenity(editing.id, payload)
    setEditing(null)
    setNotice({
      type: 'success',
      text: `Đã lưu tiện nghi ${updated.name}`,
    })
    await load()
  }

  async function handleToggle(amenity: Amenity) {
    try {
      await updateAmenityStatus(amenity.id, !amenity.active)
      setNotice({
        type: 'success',
        text: amenity.active
          ? `Đã ngừng dùng ${amenity.name}`
          : `Đã dùng lại ${amenity.name}`,
      })
      await load()
    } catch (err) {
      setNotice({
        type: 'error',
        text:
          err instanceof Error
            ? err.message
            : 'Không đổi được trạng thái',
      })
    }
  }

  async function handleDelete(amenity: Amenity) {
    if (!window.confirm(`Xoá tiện nghi "${amenity.name}"?`)) return

    try {
      await deleteAmenity(amenity.id)

      if (editing?.id === amenity.id) {
        setEditing(null)
      }

      setNotice({
        type: 'success',
        text: `Đã xoá tiện nghi ${amenity.name}`,
      })

      await load()
    } catch (err) {
      setNotice({
        type: 'error',
        text:
          err instanceof Error
            ? err.message
            : 'Không xoá được tiện nghi',
      })
    }
  }

  return (
    <div className="amenity-page">
     
      

      <section className="amenity-summary" aria-label="Tổng quan tiện nghi">
        <article className="amenity-summary-card">
          <span className="amenity-summary-icon">✦</span>
          <div>
            <span>Tổng tiện nghi</span>
            <strong>{stats.total}</strong>
          </div>
        </article>

        <article className="amenity-summary-card">
          <span className="amenity-summary-icon">✓</span>
          <div>
            <span>Đang dùng</span>
            <strong>{stats.active}</strong>
          </div>
        </article>

        <article className="amenity-summary-card">
          <span className="amenity-summary-icon">⌘</span>
          <div>
            <span>Đã gắn loại phòng</span>
            <strong>{stats.linked}</strong>
          </div>
        </article>
      </section>

      {notice && (
        <div className={`amenity-notice ${notice.type}`} role="status">
          {notice.text}
        </div>
      )}

      <section
        className={
          canManage
            ? 'amenity-workspace'
            : 'amenity-workspace readonly'
        }
      >
        <div className="amenity-list-panel">
          <div className="amenity-section-heading">
            <div>
              <span className="amenity-kicker">DANH SÁCH</span>
              <h2>Danh mục tiện nghi</h2>
              <p>
                Theo dõi biểu tượng, trạng thái và số loại phòng đang sử dụng.
              </p>
            </div>

            <span className="amenity-list-count">
              {amenities.length} tiện nghi
            </span>
          </div>

          {isLoading ? (
            <div className="amenity-empty">Đang tải...</div>
          ) : loadError ? (
            <div className="amenity-notice error" role="alert">
              {loadError}
            </div>
          ) : amenities.length === 0 ? (
            <div className="amenity-empty">
              Chưa có tiện nghi nào.
            </div>
          ) : (
            <div className="amenity-grid">
              {amenities.map((amenity) => (
                <article className="amenity-card" key={amenity.id}>
                  <div className="amenity-card-top">
                    <div className="amenity-identity">
                      <div className="amenity-icon">
                        {amenity.icon || '✦'}
                      </div>

                      <div>
                        <span className="amenity-code">{amenity.code}</span>
                        <h3>{amenity.name}</h3>
                      </div>
                    </div>

                    <span
                      className={
                        amenity.active
                          ? 'amenity-status active'
                          : 'amenity-status inactive'
                      }
                    >
                      {amenity.active ? 'Đang dùng' : 'Ngừng dùng'}
                    </span>
                  </div>

                  <div className="amenity-meta">
                    <div>
                      <span>Số loại phòng</span>
                      <strong>{amenity.roomTypeCount}</strong>
                    </div>

                    <p>
                      {amenity.roomTypeCount > 0
                        ? `Đang được sử dụng trong ${amenity.roomTypeCount} loại phòng.`
                        : 'Chưa được gắn vào loại phòng nào.'}
                    </p>
                  </div>

                  {canManage && (
                    <div className="amenity-actions">
                      <button
                        className="secondary-button"
                        type="button"
                        onClick={() => setEditing(amenity)}
                      >
                        Sửa
                      </button>

                      <button
                        className="secondary-button"
                        type="button"
                        onClick={() => void handleToggle(amenity)}
                      >
                        {amenity.active ? 'Ngừng dùng' : 'Dùng lại'}
                      </button>

                      <button
                        className="secondary-button danger"
                        type="button"
                        title={
                          amenity.roomTypeCount > 0
                            ? 'Đang được gắn cho loại phòng, chỉ có thể ngừng dùng'
                            : 'Xoá tiện nghi'
                        }
                        onClick={() => void handleDelete(amenity)}
                      >
                        Xoá
                      </button>
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
        </div>

        {canManage && (
          <aside className="amenity-form-panel">
            <div className="amenity-section-heading compact">
              <div>
                <span className="amenity-kicker">
                  {editing ? 'CHỈNH SỬA' : 'THÊM MỚI'}
                </span>

                <h2>
                  {editing
                    ? `Sửa tiện nghi ${editing.code}`
                    : 'Thêm tiện nghi'}
                </h2>

                <p>
                  Tiện nghi ngừng dùng sẽ không hiện ở loại phòng.
                </p>
              </div>
            </div>

            <AmenityForm
              key={editing?.id ?? 'create'}
              initial={editing ?? undefined}
              onSubmit={editing ? handleUpdate : handleCreate}
              onCancel={editing ? () => setEditing(null) : undefined}
            />
          </aside>
        )}
      </section>
    </div>
  )
}