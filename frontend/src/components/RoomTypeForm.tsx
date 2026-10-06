import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import type { Amenity } from '../types/amenity'
import type { RoomType, RoomTypePayload } from '../types/roomType'

const MAX_IMAGE_FILES = 8
const MAX_IMAGE_FILE_SIZE_BYTES = 5 * 1024 * 1024

type PendingRoomTypeImage = {
  id: string
  file: File
  url: string
}

type RoomTypeFormProps = {
  initial?: RoomType
  amenities: Amenity[]
  onSubmit: (payload: RoomTypePayload, files?: File[]) => Promise<void>
  onCancel?: () => void
}

type FormState = {
  code: string
  name: string
  standardCapacity: string
  maxCapacity: string
  extraGuestFee: string
  numberOfBeds: string
  description: string
  weekdayPrice: string
  weekendPrice: string
}

function toFormState(roomType?: RoomType): FormState {
  return {
    code: roomType?.code ?? '',
    name: roomType?.name ?? '',
    standardCapacity: roomType ? String(roomType.standardCapacity) : '2',
    maxCapacity: roomType ? String(roomType.maxCapacity) : '2',
    extraGuestFee: roomType ? String(roomType.extraGuestFee ?? 0) : '0',
    numberOfBeds: roomType ? String(roomType.numberOfBeds) : '1',
    description: roomType?.description ?? '',
    weekdayPrice:
      roomType?.weekdayPrice != null
        ? String(roomType.weekdayPrice)
        : '',
    weekendPrice:
      roomType?.weekendPrice != null
        ? String(roomType.weekendPrice)
        : '',
  }
}

/**
 * S1-06 AC1, AC2: mã, tên, sức chứa tiêu chuẩn, sức chứa tối đa,
 * số giường và mô tả.
 * S1-08: tick chọn tiện nghi ngay trong biểu mẫu thêm / sửa loại phòng.
 * S2-01:
 * - Lát 1: giá ngày thường.
 * - Lát 2: giá cuối tuần.
 */
export function RoomTypeForm({
  initial,
  amenities,
  onSubmit,
  onCancel,
}: RoomTypeFormProps) {
  const [form, setForm] = useState<FormState>(() => toFormState(initial))

  const [amenityIds, setAmenityIds] = useState<number[]>(
    () => initial?.amenities.map((amenity) => amenity.id) ?? [],
  )

  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [pendingImages, setPendingImages] = useState<PendingRoomTypeImage[]>([])
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    return () => {
      pendingImages.forEach((image) => URL.revokeObjectURL(image.url))
    }
  }, [pendingImages])

  // S1-08 AC4: tiện nghi đã ngừng dùng không hiện, nên cũng không cho tick.
  const activeAmenities = amenities.filter((amenity) => amenity.active)

  function update(field: keyof FormState, value: string) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))
  }

  function toggleAmenity(id: number) {
    setAmenityIds((current) =>
      current.includes(id)
        ? current.filter((existing) => existing !== id)
        : [...current, id],
    )
  }

  function handlePendingImageFiles(fileList: FileList | File[]) {
    const nextFiles = Array.from(fileList)
    if (nextFiles.length === 0) return

    const roomForSelection = MAX_IMAGE_FILES - pendingImages.length
    if (roomForSelection <= 0) {
      setError('Mỗi loại phòng chỉ được tối đa 8 ảnh.')
      return
    }

    const accepted: File[] = []
    const invalidTypeCount = nextFiles.filter((file) => {
      const isAcceptedType = file.type === 'image/jpeg' || file.type === 'image/png' || /\.(jpg|jpeg|png)$/i.test(file.name)
      return !isAcceptedType
    }).length

    if (invalidTypeCount > 0 && invalidTypeCount === nextFiles.length) {
      setError('Chỉ hỗ trợ ảnh JPG hoặc PNG.')
      return
    }

    for (const file of nextFiles) {
      const isAcceptedType = file.type === 'image/jpeg' || file.type === 'image/png' || /\.(jpg|jpeg|png)$/i.test(file.name)
      if (!isAcceptedType) {
        continue
      }

      if (file.size > MAX_IMAGE_FILE_SIZE_BYTES) {
        continue
      }

      accepted.push(file)
    }

    if (accepted.length === 0) {
      setError('Mỗi ảnh không được vượt quá 5MB và chỉ hỗ trợ JPG hoặc PNG.')
      return
    }

    const availableFiles = accepted.slice(0, roomForSelection)
    if (accepted.length > availableFiles.length) {
      setError('Mỗi loại phòng chỉ được tối đa 8 ảnh.')
    }

    setPendingImages((current) => [
      ...current,
      ...availableFiles.map((file) => ({
        id: `${file.name}-${file.lastModified}-${file.size}-${Math.random().toString(16).slice(2)}`,
        file,
        url: URL.createObjectURL(file),
      })),
    ])
    setError(null)

    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  function removePendingImage(id: string) {
    setPendingImages((current) => {
      const imageToRemove = current.find((image) => image.id === id)
      if (imageToRemove) {
        URL.revokeObjectURL(imageToRemove.url)
      }

      return current.filter((image) => image.id !== id)
    })
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    const standardCapacity = Number(form.standardCapacity)
    const maxCapacity = Number(form.maxCapacity)
    const extraGuestFee = Number(form.extraGuestFee)
    const numberOfBeds = Number(form.numberOfBeds)
    const weekdayPrice = Number(form.weekdayPrice)
    const weekendPrice = Number(form.weekendPrice)

    if (!form.code.trim() || !form.name.trim()) {
      setError('Vui lòng nhập mã và tên loại phòng')
      return
    }

    if (
      ![standardCapacity, maxCapacity, numberOfBeds].every(
        (value) => Number.isInteger(value) && value >= 1,
      )
    ) {
      setError('Sức chứa và số giường phải là số nguyên từ 1 trở lên')
      return
    }

    if (!Number.isInteger(extraGuestFee) || extraGuestFee < 0) {
      setError('Phụ thu thêm người phải là số nguyên từ 0 trở lên (VND)')
      return
    }

    if (
      form.weekdayPrice.trim() === '' ||
      !Number.isInteger(weekdayPrice) ||
      weekdayPrice <= 0
    ) {
      setError('Giá ngày thường phải là số nguyên lớn hơn 0')
      return
    }

    if (
      form.weekendPrice.trim() === '' ||
      !Number.isInteger(weekendPrice) ||
      weekendPrice <= 0
    ) {
      setError('Giá cuối tuần phải là số nguyên lớn hơn 0')
      return
    }

    // AC2: báo ngay trên giao diện, máy chủ vẫn kiểm tra lại.
    if (maxCapacity < standardCapacity) {
      setError(
        `Sức chứa tối đa (${maxCapacity}) không được nhỏ hơn sức chứa tiêu chuẩn (${standardCapacity})`,
      )
      return
    }

    setIsSaving(true)

    try {
      await onSubmit(
        {
          code: form.code.trim(),
          name: form.name.trim(),
          standardCapacity,
          maxCapacity,
          extraGuestFee,
          numberOfBeds,
          description: form.description.trim(),
          amenityIds,
          weekdayPrice,
          weekendPrice,
        },
        !initial ? pendingImages.map((image) => image.file) : undefined,
      )

      if (!initial) {
        setForm(toFormState())
        setAmenityIds([])
        setPendingImages([])
      }
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Không lưu được loại phòng',
      )
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <form
      className="checkin-form"
      onSubmit={handleSubmit}
      noValidate
    >
      {error && (
        <div
          className="alert"
          role="alert"
        >
          {error}
        </div>
      )}

      <div className="room-type-form-row">
        <label className="form-label">
          Mã loại phòng

          <input
            className="form-control"
            value={form.code}
            placeholder="DOI"
            maxLength={50}
            onChange={(event) =>
              update('code', event.target.value)
            }
          />
        </label>

        <label className="form-label">
          Tên loại phòng

          <input
            className="form-control"
            value={form.name}
            placeholder="Phòng đôi"
            maxLength={80}
            onChange={(event) =>
              update('name', event.target.value)
            }
          />
        </label>
      </div>

      <div className="room-type-form-row">
        <label className="form-label">
          Sức chứa tiêu chuẩn (người)

          <input
            className="form-control"
            type="number"
            min={1}
            value={form.standardCapacity}
            onChange={(event) =>
              update(
                'standardCapacity',
                event.target.value,
              )
            }
          />
        </label>

        <label className="form-label">
          Sức chứa tối đa (người)

          <input
            className="form-control"
            type="number"
            min={1}
            value={form.maxCapacity}
            onChange={(event) =>
              update(
                'maxCapacity',
                event.target.value,
              )
            }
          />
        </label>
      </div>

      <div className="room-type-form-row">
        <label className="form-label">
          Phụ thu thêm người (VND/người/đêm)

          <input
            className="form-control"
            type="number"
            min={0}
            step="1"
            inputMode="numeric"
            value={form.extraGuestFee}
            placeholder="250000"
            onChange={(event) => update('extraGuestFee', event.target.value)}
          />
        </label>

        <label className="form-label">
          Số giường

          <input
            className="form-control"
            type="number"
            min={1}
            value={form.numberOfBeds}
            onChange={(event) =>
              update(
                'numberOfBeds',
                event.target.value,
              )
            }
          />
        </label>
      </div>

      <div className="room-type-form-row">
        <label className="form-label">
          Giá ngày thường (VND/đêm)

          <input
            className="form-control"
            type="number"
            min="1"
            step="1"
            inputMode="numeric"
            value={form.weekdayPrice}
            placeholder="Ví dụ: 500000"
            onChange={(event) =>
              update(
                'weekdayPrice',
                event.target.value,
              )
            }
          />
        </label>

        <label className="form-label">
          Giá cuối tuần (VND/đêm)

          <input
            className="form-control"
            type="number"
            min="1"
            step="1"
            inputMode="numeric"
            value={form.weekendPrice}
            placeholder="Ví dụ: 650000"
            onChange={(event) =>
              update(
                'weekendPrice',
                event.target.value,
              )
            }
          />
        </label>
      </div>

      <label className="form-label full-width">
        Mô tả

        <textarea
          className="form-control"
          rows={3}
          maxLength={500}
          value={form.description}
          onChange={(event) =>
            update(
              'description',
              event.target.value,
            )
          }
        />
      </label>

      {!initial && (
        <div className="room-type-form-image-section">
          <div className="room-type-form-section-header">
            <span className="room-type-kicker">HÌNH ẢNH LOẠI PHÒNG</span>
          </div>

          <div className="room-type-form-image-meta">
            <span>JPG, PNG</span>
            <span>Tối đa 5MB/ảnh</span>
            <span>Tối đa 8 ảnh</span>
          </div>

          <label className="room-type-form-upload-button">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png"
              multiple
              onChange={(event: ChangeEvent<HTMLInputElement>) => {
                if (event.target.files) {
                  handlePendingImageFiles(event.target.files)
                }
              }}
            />
            <span>Chọn ảnh từ máy tính</span>
          </label>

          {pendingImages.length > 0 && (
            <div className="room-type-form-preview-grid">
              {pendingImages.map((image, index) => (
                <div key={image.id} className="room-type-form-preview-item">
                  <img src={image.url} alt={`Preview ${index + 1}`} />
                  <div className="room-type-form-preview-badge">#{index + 1}</div>
                  {index === 0 && <div className="room-type-form-primary-badge">★ Đại diện</div>}
                  <button
                    type="button"
                    className="room-type-form-preview-remove"
                    onClick={() => removePendingImage(image.id)}
                    aria-label={`Xoá ảnh ${index + 1}`}
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <fieldset className="amenity-checklist full-width">
        <legend>
          Tiện nghi ({amenityIds.length} đã chọn)
        </legend>

        {activeAmenities.length === 0 ? (
          <p className="checkin-note">
            Chưa có tiện nghi đang dùng. Khai báo ở trang Tiện nghi.
          </p>
        ) : (
          activeAmenities.map((amenity) => (
            <label
              key={amenity.id}
              className="amenity-check"
            >
              <input
                type="checkbox"
                checked={amenityIds.includes(
                  amenity.id,
                )}
                onChange={() =>
                  toggleAmenity(amenity.id)
                }
              />

              <span>
                {amenity.icon} {amenity.name}
              </span>
            </label>
          ))
        )}
      </fieldset>

      <div className="room-type-form-actions">
        <button
          className="primary-button"
          type="submit"
          disabled={isSaving}
        >
          {isSaving
            ? 'Đang lưu...'
            : initial
              ? 'Lưu thay đổi'
              : 'Thêm loại phòng'}
        </button>

        {onCancel && (
          <button
            className="secondary-button"
            type="button"
            onClick={onCancel}
          >
            Huỷ
          </button>
        )}
      </div>

      {initial && initial.roomCount > 0 && (
        <p className="checkin-note">
          Đổi tên sẽ cập nhật luôn tên loại phòng của{' '}
          {initial.roomCount} phòng đang gắn vào.
        </p>
      )}
    </form>
  )
}