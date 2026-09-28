import { useCallback, useEffect, useState } from 'react'
import { RoomTypeForm } from '../components/RoomTypeForm'
import { hasPermission } from '../permissions/rolePermissions'
import {
  createRoomType,
  deleteRoomType,
  getRoomTypes,
  updateRoomType,
  updateRoomTypeStatus,
} from '../services/roomTypeService'
import type { RoomType, RoomTypePayload } from '../types/roomType'
import '../App.css'
import './UserManagementPage.css'

type RoomTypePageProps = {
  role: string
}

type Notice = { type: 'success' | 'error'; text: string }

/** S1-06: Chủ homestay và Quản trị thêm/sửa/ngừng bán/xoá loại phòng, Lễ tân chỉ xem. */
export function RoomTypePage({ role }: RoomTypePageProps) {
  const canManage = hasPermission(role, 'roomTypes:manage')
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [editing, setEditing] = useState<RoomType | null>(null)

  const load = useCallback(() =>
    getRoomTypes()
      .then((items) => {
        setRoomTypes(items)
        setLoadError(null)
      })
      .catch((err: unknown) => {
        setLoadError(err instanceof Error ? err.message : 'Không tải được danh sách loại phòng')
      })
      .finally(() => {
        setIsLoading(false)
      }),
  [])

  useEffect(() => {
    void load()
  }, [load])

  async function handleCreate(payload: RoomTypePayload) {
    const created = await createRoomType(payload)
    setNotice({ type: 'success', text: `Đã thêm loại phòng ${created.name}` })
    await load()
  }

  async function handleUpdate(payload: RoomTypePayload) {
    if (!editing) return
    const updated = await updateRoomType(editing.id, payload)
    setEditing(null)
    setNotice({ type: 'success', text: `Đã lưu loại phòng ${updated.name}` })
    await load()
  }

  async function handleToggle(roomType: RoomType) {
    try {
      await updateRoomTypeStatus(roomType.id, !roomType.active)
      setNotice({
        type: 'success',
        text: roomType.active ? `Đã ngừng bán ${roomType.name}` : `Đã mở bán lại ${roomType.name}`,
      })
      await load()
    } catch (err) {
      setNotice({ type: 'error', text: err instanceof Error ? err.message : 'Không đổi được trạng thái' })
    }
  }

  async function handleDelete(roomType: RoomType) {
    if (!window.confirm(`Xoá loại phòng "${roomType.name}"?`)) return
    try {
      await deleteRoomType(roomType.id)
      if (editing?.id === roomType.id) setEditing(null)
      setNotice({ type: 'success', text: `Đã xoá loại phòng ${roomType.name}` })
      await load()
    } catch (err) {
      // AC4: máy chủ báo "đang có N phòng gắn vào nên không xoá được..."
      setNotice({ type: 'error', text: err instanceof Error ? err.message : 'Không xoá được loại phòng' })
    }
  }

  return (
    <div className="app-shell">
      <main className="main-content">
        <section className="intro">
          <div>
            <p className="eyebrow">Danh mục phòng · S1-06</p>
            <h1>Loại phòng</h1>
            <p>
              {canManage
                ? 'Khai báo loại phòng để mọi người cùng gọi tên và bán theo cùng một mô tả.'
                : 'Xem các loại phòng homestay đang bán.'}
            </p>
          </div>
        </section>

        <section className="workspace-grid">
          <div className="panel">
            <div className="panel-heading">
              <div>
                <h2>Danh sách loại phòng</h2>
                <p>{roomTypes.length} loại phòng</p>
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
            ) : roomTypes.length === 0 ? (
              <p className="empty-state">Chưa có loại phòng nào.</p>
            ) : (
              <div className="user-table-wrapper">
                <table className="user-table">
                  <thead>
                    <tr>
                      <th>Mã</th>
                      <th>Tên</th>
                      <th>Sức chứa</th>
                      <th>Số giường</th>
                      <th>Số phòng</th>
                      <th>Trạng thái</th>
                      {canManage && <th>Thao tác</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {roomTypes.map((roomType) => (
                      <tr key={roomType.id}>
                        <td>{roomType.code}</td>
                        <td>
                          {roomType.name}
                          {roomType.description && <small>{roomType.description}</small>}
                        </td>
                        <td>
                          {roomType.standardCapacity} – {roomType.maxCapacity} người
                        </td>
                        <td>{roomType.numberOfBeds}</td>
                        <td>{roomType.roomCount}</td>
                        <td>
                          <span className={`user-tag ${roomType.active ? '' : 'off'}`}>
                            {roomType.active ? 'Đang bán' : 'Ngừng bán'}
                          </span>
                        </td>
                        {canManage && (
                          <td>
                            <div className="user-actions">
                              <button className="secondary-button" type="button"
                                onClick={() => setEditing(roomType)}>
                                Sửa
                              </button>
                              <button className="secondary-button" type="button"
                                onClick={() => handleToggle(roomType)}>
                                {roomType.active ? 'Ngừng bán' : 'Bán lại'}
                              </button>
                              <button className="secondary-button" type="button"
                                title={roomType.roomCount > 0
                                  ? 'Đang có phòng gắn vào, chỉ có thể ngừng bán'
                                  : 'Xoá loại phòng'}
                                onClick={() => handleDelete(roomType)}>
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
                  <h2>{editing ? `Sửa loại phòng ${editing.code}` : 'Thêm loại phòng'}</h2>
                  <p>Sức chứa tối đa không được nhỏ hơn sức chứa tiêu chuẩn.</p>
                </div>
              </div>
              {editing ? (
                <RoomTypeForm
                  key={editing.id}
                  initial={editing}
                  onSubmit={handleUpdate}
                  onCancel={() => setEditing(null)}
                />
              ) : (
                <RoomTypeForm onSubmit={handleCreate} />
              )}
            </aside>
          )}
        </section>
      </main>
    </div>
  )
}