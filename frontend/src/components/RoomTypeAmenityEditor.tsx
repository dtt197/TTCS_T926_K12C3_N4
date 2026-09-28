import { useState } from 'react'
import { addRoomTypeAmenity, removeRoomTypeAmenity } from '../services/roomTypeService'
import type { Amenity } from '../types/amenity'
import type { RoomType } from '../types/roomType'

type RoomTypeAmenityEditorProps = {
  roomType: RoomType
  amenities: Amenity[]
  onChanged: (updated: RoomType) => void
}

/** S1-08 AC2: một loại phòng gắn được nhiều tiện nghi; gắn trùng bị chặn. */
export function RoomTypeAmenityEditor({ roomType, amenities, onChanged }: RoomTypeAmenityEditorProps) {
  const [selectedId, setSelectedId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  const attachedIds = new Set(roomType.amenities.map((amenity) => amenity.id))
  // Chỉ gợi ý tiện nghi đang dùng và chưa gắn; máy chủ vẫn chặn gắn trùng (AC2).
  const available = amenities.filter((amenity) => amenity.active && !attachedIds.has(amenity.id))

  async function run(action: () => Promise<RoomType>) {
    setError(null)
    setIsSaving(true)
    try {
      onChanged(await action())
      setSelectedId('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không cập nhật được tiện nghi')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="checkin-form">
      <h3>Tiện nghi của {roomType.name}</h3>
      {error && <div className="alert" role="alert">{error}</div>}
      {roomType.amenities.length === 0 ? (
        <p className="checkin-note">Chưa gắn tiện nghi nào.</p>
      ) : (
        <div className="user-actions">
          {roomType.amenities.map((amenity) => (
            <button key={amenity.id} type="button" className="secondary-button" disabled={isSaving}
              title="Bấm để bỏ tiện nghi này"
              onClick={() => run(() => removeRoomTypeAmenity(roomType.id, amenity.id))}>
              {amenity.icon} {amenity.name} ✕
            </button>
          ))}
        </div>
      )}
      <label className="form-label">
        Gắn thêm tiện nghi
        <select className="form-control" value={selectedId} onChange={(e) => setSelectedId(e.target.value)}>
          <option value="">Chọn tiện nghi...</option>
          {available.map((amenity) => (
            <option key={amenity.id} value={amenity.id}>{amenity.icon} {amenity.name}</option>
          ))}
        </select>
      </label>
      <button className="primary-button" type="button" disabled={!selectedId || isSaving}
        onClick={() => run(() => addRoomTypeAmenity(roomType.id, Number(selectedId)))}>
        Gắn tiện nghi
      </button>
    </div>
  )
}