import { useCallback, useEffect, useRef, useState, type DragEvent } from 'react'
import { getRoomTypeImages, uploadRoomTypeImage } from '../services/roomTypeService'
import type { RoomType, RoomTypeImage } from '../types/roomType'
import './RoomTypeImageModal.css'

type RoomTypeImageModalProps = {
  roomType: RoomType
  canManage: boolean
  onClose: () => void
  onImagesUpdated: () => void
}

const MAX_IMAGES = 8
const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5MB

export function RoomTypeImageModal({
  roomType,
  canManage,
  onClose,
  onImagesUpdated,
}: RoomTypeImageModalProps) {
  const [images, setImages] = useState<RoomTypeImage[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isUploading, setIsUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [previewImage, setPreviewImage] = useState<RoomTypeImage | null>(null)

  const fileInputRef = useRef<HTMLInputElement>(null)

  const fetchImages = useCallback(async () => {
    try {
      setIsLoading(true)
      setError(null)
      const data = await getRoomTypeImages(roomType.id)
      setImages(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không tải được danh sách ảnh')
    } finally {
      setIsLoading(false)
    }
  }, [roomType.id])

  useEffect(() => {
    void fetchImages()
  }, [fetchImages])

  async function handleFileProcess(file: File) {
    setError(null)

    // Kiểm tra định dạng (JPG hoặc PNG)
    const validTypes = ['image/jpeg', 'image/png']
    const hasValidExt = /\.(jpg|jpeg|png)$/i.test(file.name)
    if (!validTypes.includes(file.type) && !hasValidExt) {
      setError('Định dạng tệp không hợp lệ. Chỉ chấp nhận tệp JPG hoặc PNG.')
      return
    }

    // Kiểm tra kích thước tối đa 5MB
    if (file.size > MAX_FILE_SIZE_BYTES) {
      setError('Kích thước tệp vượt quá giới hạn tối đa 5MB.')
      return
    }

    // Kiểm tra giới hạn 8 ảnh
    if (images.length >= MAX_IMAGES) {
      setError('Mỗi loại phòng chỉ được tối đa 8 ảnh.')
      return
    }

    try {
      setIsUploading(true)
      await uploadRoomTypeImage(roomType.id, file)
      await fetchImages()
      onImagesUpdated()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Tải ảnh lên thất bại.')
    } finally {
      setIsUploading(false)
    }
  }

  function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (file) {
      void handleFileProcess(file)
    }
    // reset input để cho phép chọn lại cùng 1 file nếu cần
    event.target.value = ''
  }

  function handleDragOver(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    if (!isUploading && images.length < MAX_IMAGES && canManage) {
      setIsDragging(true)
    }
  }

  function handleDragLeave(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setIsDragging(false)
  }

  function handleDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setIsDragging(false)
    if (!canManage || isUploading || images.length >= MAX_IMAGES) return

    const file = e.dataTransfer.files?.[0]
    if (file) {
      void handleFileProcess(file)
    }
  }

  const isFull = images.length >= MAX_IMAGES

  return (
    <div className="room-type-image-modal-backdrop" onClick={onClose}>
      <div
        className="room-type-image-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="room-type-image-modal-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="room-type-image-modal-header">
          <div>
            <span className="room-type-image-modal-kicker">HÌNH ẢNH LOẠI PHÒNG</span>
            <h2 id="room-type-image-modal-title">
              Quản lý ảnh: {roomType.name} ({roomType.code})
            </h2>
            <p>Tải ảnh lên, xem bản thu nhỏ và tự động xác định ảnh đại diện.</p>
          </div>
          <button
            className="room-type-image-modal-close"
            type="button"
            aria-label="Đóng"
            onClick={onClose}
          >
            ×
          </button>
        </div>

        <div className="room-type-image-modal-body">
          <div className="room-type-image-info-bar">
            <div className="room-type-image-badges">
              <span className="room-type-image-badge-item">JPG, PNG</span>
              <span className="room-type-image-badge-item">Tối đa 5MB/ảnh</span>
              <span className="room-type-image-badge-item">Nén tối đa 1600px</span>
              <span className="room-type-image-badge-item">Tự sinh bản thu nhỏ</span>
            </div>
            <span className={`room-type-image-count-badge ${isFull ? 'full' : ''}`}>
              {images.length} / {MAX_IMAGES} ảnh
            </span>
          </div>

          {error && (
            <div className="room-type-image-error" role="alert">
              <span>⚠️</span>
              <span>{error}</span>
            </div>
          )}

          {canManage && (
            <div
              className={`room-type-upload-zone ${isDragging ? 'dragging' : ''} ${
                isFull || isUploading ? 'disabled' : ''
              }`}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => {
                if (!isFull && !isUploading) {
                  fileInputRef.current?.click()
                }
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                className="room-type-upload-input"
                accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                disabled={isFull || isUploading}
                onChange={handleFileChange}
              />

              {isUploading ? (
                <div className="room-type-upload-loading">
                  <div className="room-type-upload-spinner" />
                  <span>Đang tải lên và xử lý ảnh...</span>
                </div>
              ) : isFull ? (
                <div>
                  <span className="room-type-upload-icon">🔒</span>
                  <div className="room-type-upload-title">Đã đạt tối đa 8 ảnh</div>
                  <p className="room-type-upload-subtitle">
                    Loại phòng này đã có đủ 8 ảnh, không thể tải thêm.
                  </p>
                </div>
              ) : (
                <div>
                  <span className="room-type-upload-icon">🖼️</span>
                  <div className="room-type-upload-title">
                    Kéo thả ảnh vào đây hoặc <span>chọn tệp từ máy tính</span>
                  </div>
                  <p className="room-type-upload-subtitle">
                    Hỗ trợ tệp JPG, PNG dung lượng dưới 5MB. Ảnh đầu tiên sẽ làm ảnh đại diện.
                  </p>
                </div>
              )}
            </div>
          )}

          <div>
            <div className="room-type-image-grid-title">
              <span>Danh sách ảnh ({images.length})</span>
              <span className="room-type-image-grid-hint">
                Ảnh đầu tiên là ảnh đại diện hiển thị cho khách
              </span>
            </div>

            {isLoading ? (
              <div className="room-type-image-empty">Đang tải danh sách ảnh...</div>
            ) : images.length === 0 ? (
              <div className="room-type-image-empty">
                Chưa có ảnh nào được tải lên cho loại phòng này. Hãy tải ảnh đầu tiên để làm ảnh đại diện.
              </div>
            ) : (
              <div className="room-type-image-gallery">
                {images.map((img, index) => {
                  const isPrimary = img.isPrimary || index === 0
                  return (
                    <div
                      key={img.id}
                      className={`room-type-image-card ${isPrimary ? 'primary' : ''}`}
                      onClick={() => setPreviewImage(img)}
                      title="Nhấn để xem ảnh phóng to"
                    >
                      <img
                        src={img.thumbnailUrl}
                        alt={`Ảnh loại phòng ${roomType.name} #${index + 1}`}
                        className="room-type-image-img"
                        loading="lazy"
                      />

                      {isPrimary && (
                        <div className="room-type-image-primary-badge">
                          <span>★</span>
                          <span>Ảnh đại diện</span>
                        </div>
                      )}

                      <div className="room-type-image-order-badge">#{index + 1}</div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>

        <div className="room-type-image-modal-footer">
          <button className="room-type-image-modal-done-btn" type="button" onClick={onClose}>
            Hoàn tất
          </button>
        </div>
      </div>

      {previewImage && (
        <div
          className="room-type-lightbox-backdrop"
          onClick={(e) => {
            e.stopPropagation()
            setPreviewImage(null)
          }}
        >
          <div className="room-type-lightbox-content" onClick={(e) => e.stopPropagation()}>
            <img
              src={previewImage.imageUrl}
              alt="Ảnh phóng to"
              className="room-type-lightbox-img"
            />
            <div className="room-type-lightbox-caption">
              {previewImage.isPrimary ? '★ Ảnh đại diện - ' : ''}Thứ tự #{previewImage.displayOrder + 1} (Nhấn vùng tối để đóng)
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
