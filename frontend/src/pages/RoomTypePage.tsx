import { useCallback, useEffect, useMemo, useState } from 'react'

import { RoomTypeForm } from '../components/RoomTypeForm'
import { RoomTypeImageModal } from '../components/RoomTypeImageModal'
import { hasPermission } from '../permissions/rolePermissions'
import { getAmenities } from '../services/amenityService'
import {
  createRoomType,
  deleteRoomType,
  getRoomTypes,
  updateRoomType,
  updateRoomTypeStatus,
} from '../services/roomTypeService'
import type { Amenity } from '../types/amenity'
import type { RoomType, RoomTypePayload } from '../types/roomType'
import '../App.css'
import './RoomTypePage.css'

type RoomTypePageProps = {
  role: string
}

type Notice = {
  type: 'success' | 'error'
  text: string
}

/**
 * S1-06: Chủ homestay và Quản trị thêm/sửa/ngừng bán/xoá loại phòng,
 * Lễ tân chỉ xem.
 *
 * S1-08: gắn / bỏ tiện nghi khi sửa loại phòng;
 * danh sách chỉ hiện tiện nghi đang dùng.
 *
 * S2-01:
 * - Lát 1: giá ngày thường.
 * - Lát 2: giá cuối tuần.
 */
export function RoomTypePage({ role }: RoomTypePageProps) {
  const canManage = hasPermission(role, 'roomTypes:manage')

  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [amenities, setAmenities] = useState<Amenity[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [editing, setEditing] = useState<RoomType | null>(null)

  const [amenityDetailRoomType, setAmenityDetailRoomType] =
    useState<RoomType | null>(null)
  const [imageRoomType, setImageRoomType] = useState<RoomType | null>(null)

  const load = useCallback(
    () =>
      getRoomTypes()
        .then((items) => {
          setRoomTypes(items)
          setLoadError(null)
        })
        .catch((err: unknown) => {
          setLoadError(
            err instanceof Error
              ? err.message
              : 'Không tải được danh sách loại phòng',
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

  useEffect(() => {
    if (!canManage) return

    void getAmenities()
      .then(setAmenities)
      .catch(() => setAmenities([]))
  }, [canManage])

  const stats = useMemo(
    () => ({
      total: roomTypes.length,
      active: roomTypes.filter((roomType) => roomType.active).length,
      rooms: roomTypes.reduce(
        (sum, roomType) => sum + roomType.roomCount,
        0,
      ),
    }),
    [roomTypes],
  )

  async function handleCreate(payload: RoomTypePayload) {
    const created = await createRoomType(payload)

    setNotice({
      type: 'success',
      text: `Đã thêm loại phòng ${created.name}`,
    })

    await load()
  }

  async function handleUpdate(payload: RoomTypePayload) {
    if (!editing) return

    const updated = await updateRoomType(editing.id, payload)

    setEditing(null)

    setNotice({
      type: 'success',
      text: `Đã lưu loại phòng ${updated.name}`,
    })

    await load()
  }

  async function handleToggle(roomType: RoomType) {
    try {
      await updateRoomTypeStatus(
        roomType.id,
        !roomType.active,
      )

      setNotice({
        type: 'success',
        text: roomType.active
          ? `Đã ngừng bán ${roomType.name}`
          : `Đã mở bán lại ${roomType.name}`,
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

  async function handleDelete(roomType: RoomType) {
    if (
      !window.confirm(
        `Xoá loại phòng "${roomType.name}"?`,
      )
    ) {
      return
    }

    try {
      await deleteRoomType(roomType.id)

      if (editing?.id === roomType.id) {
        setEditing(null)
      }

      setNotice({
        type: 'success',
        text: `Đã xoá loại phòng ${roomType.name}`,
      })

      await load()
    } catch (err) {
      setNotice({
        type: 'error',
        text:
          err instanceof Error
            ? err.message
            : 'Không xoá được loại phòng',
      })
    }
  }

  return (
    <div className="room-type-page">
      <section
        className="room-type-summary"
        aria-label="Tổng quan loại phòng"
      >
        <article className="room-type-summary-card">
          <span className="room-type-summary-icon">
            ▦
          </span>

          <div>
            <span>Tổng loại phòng</span>
            <strong>{stats.total}</strong>
          </div>
        </article>

        <article className="room-type-summary-card">
          <span className="room-type-summary-icon">
            ✓
          </span>

          <div>
            <span>Đang bán</span>
            <strong>{stats.active}</strong>
          </div>
        </article>

        <article className="room-type-summary-card">
          <span className="room-type-summary-icon">
            ⌂
          </span>

          <div>
            <span>Phòng vật lý</span>
            <strong>{stats.rooms}</strong>
          </div>
        </article>
      </section>

      {notice && (
        <div
          className={`room-type-notice ${notice.type}`}
          role="status"
        >
          {notice.text}
        </div>
      )}

      <section
        className={
          canManage
            ? 'room-type-workspace'
            : 'room-type-workspace readonly'
        }
      >
        <div className="room-type-list-panel">
          <div className="room-type-section-heading">
            <div>
              <span className="room-type-kicker">
                DANH SÁCH
              </span>

              <h2>Danh sách loại phòng</h2>

              <p>
                Theo dõi sức chứa, số phòng, giá,
                tiện nghi và trạng thái bán.
              </p>
            </div>

            <span className="room-type-list-count">
              {roomTypes.length} loại
            </span>
          </div>

          {isLoading ? (
            <div className="room-type-empty">
              Đang tải...
            </div>
          ) : loadError ? (
            <div
              className="room-type-notice error"
              role="alert"
            >
              {loadError}
            </div>
          ) : roomTypes.length === 0 ? (
            <div className="room-type-empty">
              Chưa có loại phòng nào.
            </div>
          ) : (
            <div className="room-type-grid">
              {roomTypes.map((roomType) => (
                <article
                  className="room-type-card"
                  key={roomType.id}
                >
                  <div className="room-type-card-thumb-container">
                    <div
                      className="room-type-card-thumb"
                      onClick={() => setImageRoomType(roomType)}
                      title="Nhấn để xem hoặc quản lý ảnh loại phòng"
                    >
                      {roomType.avatarUrl ? (
                        <>
                          <img
                            src={roomType.avatarUrl}
                            alt={`Ảnh đại diện ${roomType.name}`}
                            loading="lazy"
                          />
                          <span className="room-type-card-thumb-badge">★ Đại diện</span>
                        </>
                      ) : (
                        <div className="room-type-card-thumb-placeholder">
                          <span style={{ fontSize: '18px' }}>📷</span>
                          <span>Chưa có ảnh</span>
                        </div>
                      )}
                    </div>

                    <div className="room-type-card-top-content">
                      <div className="room-type-card-top">
                        <div className="room-type-card-title">
                          <div className="room-type-code">
                            {roomType.code}
                          </div>

                          <div>
                            <h3 title={roomType.name}>{roomType.name}</h3>

                            <p>
                              {roomType.description ||
                                'Chưa có mô tả cho loại phòng này.'}
                            </p>
                          </div>
                        </div>

                        <span
                          className={
                            roomType.active
                              ? 'room-type-status active'
                              : 'room-type-status inactive'
                          }
                        >
                          {roomType.active
                            ? 'Đang bán'
                            : 'Ngừng bán'}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className="room-type-metrics">
                    <div>
                      <span>Sức chứa</span>

                      <strong>
                        {roomType.standardCapacity}–
                        {roomType.maxCapacity}
                      </strong>

                      <small>người</small>
                    </div>

                    <div>
                      <span>Số giường</span>

                      <strong>
                        {roomType.numberOfBeds}
                      </strong>

                      <small>giường</small>
                    </div>

                    <div>
                      <span>Giá ngày thường</span>

                      {roomType.weekdayPrice != null ? (
                        <>
                          <strong>
                            {roomType.weekdayPrice.toLocaleString(
                              'vi-VN',
                            )}
                          </strong>

                          <small>VND/đêm</small>
                        </>
                      ) : (
                        <>
                          <strong>—</strong>
                          <small>Chưa khai báo</small>
                        </>
                      )}
                    </div>

                    <div>
                      <span>Giá cuối tuần</span>

                      {roomType.weekendPrice != null ? (
                        <>
                          <strong>
                            {roomType.weekendPrice.toLocaleString(
                              'vi-VN',
                            )}
                          </strong>

                          <small>VND/đêm</small>
                        </>
                      ) : (
                        <>
                          <strong>—</strong>
                          <small>Chưa khai báo</small>
                        </>
                      )}
                    </div>

                    <div>
                      <span>Số phòng</span>

                      <strong>
                        {roomType.roomCount}
                      </strong>

                      <small>phòng</small>
                    </div>
                  </div>

                  <div className="room-type-amenity-block">
                    <span className="room-type-meta-label">
                      Tiện nghi
                    </span>

                    {roomType.amenities.length === 0 ? (
                      <p className="room-type-no-amenity">
                        Chưa gắn tiện nghi.
                      </p>
                    ) : (
                      <button
                        className="room-type-amenity-detail-button"
                        type="button"
                        onClick={() =>
                          setAmenityDetailRoomType(
                            roomType,
                          )
                        }
                      >
                        <span className="room-type-amenity-detail-icon">
                          ✦
                        </span>

                        <span className="room-type-amenity-detail-text">
                          Chi tiết tiện nghi
                          <strong>
                            {' '}
                            ({roomType.amenities.length})
                          </strong>
                        </span>

                        <span className="room-type-amenity-detail-arrow">
                          ›
                        </span>
                      </button>
                    )}
                  </div>

                  <div className="room-type-actions">
                    <button
                      className="secondary-button"
                      type="button"
                      onClick={() => setImageRoomType(roomType)}
                      title={canManage ? 'Quản lý ảnh loại phòng' : 'Xem ảnh loại phòng'}
                    >
                      🖼️ Ảnh {roomType.images && roomType.images.length > 0 ? `(${roomType.images.length})` : ''}
                    </button>

                    {canManage && (
                      <>
                        <button
                          className="secondary-button"
                          type="button"
                          onClick={() =>
                            setEditing(roomType)
                          }
                        >
                          Sửa
                        </button>

                        <button
                          className="secondary-button"
                          type="button"
                          onClick={() =>
                            void handleToggle(roomType)
                          }
                        >
                          {roomType.active
                            ? 'Ngừng bán'
                            : 'Bán lại'}
                        </button>

                        <button
                          className="secondary-button danger"
                          type="button"
                          title={
                            roomType.roomCount > 0
                              ? 'Đang có phòng gắn vào, chỉ có thể ngừng bán'
                              : 'Xoá loại phòng'
                          }
                          onClick={() =>
                            void handleDelete(roomType)
                          }
                        >
                          Xoá
                        </button>
                      </>
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>

        {canManage && (
          <aside className="room-type-form-panel">
            <div className="room-type-section-heading compact">
              <div>
                <span className="room-type-kicker">
                  {editing
                    ? 'CHỈNH SỬA'
                    : 'THÊM MỚI'}
                </span>

                <h2>
                  {editing
                    ? `Sửa loại phòng ${editing.code}`
                    : 'Thêm loại phòng'}
                </h2>

                <p>
                  Sức chứa tối đa không được nhỏ hơn
                  sức chứa tiêu chuẩn.
                </p>
              </div>
            </div>

            <RoomTypeForm
              key={editing?.id ?? 'create'}
              initial={editing ?? undefined}
              amenities={amenities}
              onSubmit={
                editing
                  ? handleUpdate
                  : handleCreate
              }
              onCancel={
                editing
                  ? () => setEditing(null)
                  : undefined
              }
            />
          </aside>
        )}
      </section>

      {amenityDetailRoomType && (
        <div
          className="room-type-amenity-modal-backdrop"
          onClick={() =>
            setAmenityDetailRoomType(null)
          }
        >
          <div
            className="room-type-amenity-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="amenity-detail-title"
            onClick={(event) =>
              event.stopPropagation()
            }
          >
            <div className="room-type-amenity-modal-header">
              <div>
                <span className="room-type-amenity-modal-kicker">
                  TIỆN NGHI PHÒNG
                </span>

                <h2 id="amenity-detail-title">
                  {amenityDetailRoomType.name}
                </h2>

                <p>
                  {amenityDetailRoomType.description ||
                    'Danh sách tiện nghi của loại phòng này.'}
                </p>
              </div>

              <button
                className="room-type-amenity-modal-close"
                type="button"
                aria-label="Đóng"
                onClick={() =>
                  setAmenityDetailRoomType(null)
                }
              >
                ×
              </button>
            </div>

            <div className="room-type-amenity-modal-summary">
              <span>Tiện nghi</span>

              <strong>
                {
                  amenityDetailRoomType
                    .amenities.length
                }{' '}
                tiện nghi
              </strong>
            </div>

            <div className="room-type-amenity-modal-grid">
              {amenityDetailRoomType.amenities.map(
                (amenity) => (
                  <div
                    className="room-type-amenity-modal-item"
                    key={amenity.id}
                  >
                    <span className="room-type-amenity-modal-icon">
                      {amenity.icon || '✦'}
                    </span>

                    <span className="room-type-amenity-modal-name">
                      {amenity.name}
                    </span>
                  </div>
                ),
              )}
            </div>

            <div className="room-type-amenity-modal-footer">
              <button
                className="room-type-amenity-modal-done"
                type="button"
                onClick={() =>
                  setAmenityDetailRoomType(null)
                }
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}

      {imageRoomType && (
        <RoomTypeImageModal
          roomType={imageRoomType}
          canManage={canManage}
          onClose={() => setImageRoomType(null)}
          onImagesUpdated={async () => {
            const updatedItems = await getRoomTypes()
            setRoomTypes(updatedItems)
            const refreshed = updatedItems.find((r) => r.id === imageRoomType.id)
            if (refreshed) {
              setImageRoomType(refreshed)
            }
          }}
        />
      )}
    </div>
  )
}