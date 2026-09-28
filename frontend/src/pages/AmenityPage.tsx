import { useCallback, useEffect, useState } from 'react'
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
import './UserManagementPage.css'

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

  const load = useCallback(() =>
    getAmenities()
      .then((items) => {
        setAmenities(items)
        setLoadError(null)
      })
      .catch((err: unknown) => {
        setLoadError(err instanceof Error ? err.message : 'Không tải được danh sách tiện nghi')
      })
      .finally(() => {
        setIsLoading(false)
      }),
  [])

  useEffect(() => {
    void load()
  }, [load])

  async function handleCreate(payload: AmenityPayload) {
    const created = await createAmenity(payload)
    setNotice({ type: 'success', text: `Đã thêm tiện nghi ${created.name}` })
    await load()
  }

  async function handleUpdate(payload: AmenityPayload) {
    if (!editing) return
    const updated = await updateAmenity(editing.id, payload)
    setEditing(null)
    setNotice({ type: 'success', text: `Đã lưu tiện nghi ${updated.name}` })
    await load()
  }

  async function handleToggle(amenity: Amenity) {
    try {
      await updateAmenityStatus(amenity.id, !amenity.active)
      setNotice({
        type: 'success',
        text: amenity.active ? `Đã ngừng dùng ${amenity.name}` : `Đã dùng lại ${amenity.name}`,
      })
      await load()
    } catch (err) {
      setNotice({ type: 'error', text: err instanceof Error ? err.message : 'Không đổi được trạng thái' })
    }
  }

  async function handleDelete(amenity: Amenity) {
    if (!window.confirm(`Xoá tiện nghi "${amenity.name}"?`)) return
    try {
      await deleteAmenity(amenity.id)
      if (editing?.id === amenity.id) setEditing(null)
      setNotice({ type: 'success', text: `Đã xoá tiện nghi ${amenity.name}` })
      await load()
    } catch (err) {
      // AC3: máy chủ báo "đang được gắn cho N loại phòng nên không xoá được..."
      setNotice({ type: 'error', text: err instanceof Error ? err.message : 'Không xoá được tiện nghi' })
    }
  }

  return (
    <div className="app-shell">
      <main className="main-content">
        <section className="intro">
          <div>
            <p className="eyebrow">Danh mục phòng · S1-08</p>
            <h1>Tiện nghi</h1>
            <p>
              {canManage
                ? 'Khai báo tiện nghi rồi gắn vào từng loại phòng ở trang Loại phòng.'
                : 'Xem danh mục tiện nghi của homestay.'}
            </p>
          </div>
        </section>

        <section className="workspace-grid">
          <div className="panel">
            <div className="panel-heading">
              <div>
                <h2>Danh mục tiện nghi</h2>
                <p>{amenities.length} tiện nghi</p>
              </div>
            </div>

            {notice && (
              <div className={`alert ${notice.type === 'success' ? 'success' : ''}`} role="status">
                {notice.text}
              </div>
            )}

            {isLoading ? (
              <p className="empty-state">Đang tải...</p>
            ) : loadError ? (
              <div className="alert" role="alert">{loadError}</div>
            ) : amenities.length === 0 ? (
              <p className="empty-state">Chưa có tiện nghi nào.</p>
            ) : (
              <div className="user-table-wrapper">
                <table className="user-table">
                  <thead>
                    <tr>
                      <th>Biểu tượng</th>
                      <th>Mã</th>
                      <th>Tên</th>
                      <th>Số loại phòng</th>
                      <th>Trạng thái</th>
                      {canManage && <th>Thao tác</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {amenities.map((amenity) => (
                      <tr key={amenity.id}>
                        <td>{amenity.icon}</td>
                        <td>{amenity.code}</td>
                        <td>{amenity.name}</td>
                        <td>{amenity.roomTypeCount}</td>
                        <td>
                          <span className={`user-tag ${amenity.active ? '' : 'off'}`}>
                            {amenity.active ? 'Đang dùng' : 'Ngừng dùng'}
                          </span>
                        </td>
                        {canManage && (
                          <td>
                            <div className="user-actions">
                              <button className="secondary-button" type="button"
                                onClick={() => setEditing(amenity)}>
                                Sửa
                              </button>
                              <button className="secondary-button" type="button"
                                onClick={() => handleToggle(amenity)}>
                                {amenity.active ? 'Ngừng dùng' : 'Dùng lại'}
                              </button>
                              <button className="secondary-button" type="button"
                                title={amenity.roomTypeCount > 0
                                  ? 'Đang được gắn cho loại phòng, chỉ có thể ngừng dùng'
                                  : 'Xoá tiện nghi'}
                                onClick={() => handleDelete(amenity)}>
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
            <aside className="panel">
              <div className="panel-heading">
                <div>
                  <h2>{editing ? `Sửa tiện nghi ${editing.code}` : 'Thêm tiện nghi'}</h2>
                  <p>Tiện nghi ngừng dùng sẽ không hiện ở loại phòng.</p>
                </div>
              </div>
              {editing ? (
                <AmenityForm
                  key={editing.id}
                  initial={editing}
                  onSubmit={handleUpdate}
                  onCancel={() => setEditing(null)}
                />
              ) : (
                <AmenityForm onSubmit={handleCreate} />
              )}
            </aside>
          )}
        </section>
      </main>
    </div>
  )
}