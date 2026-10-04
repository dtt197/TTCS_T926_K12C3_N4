import { useCallback, useEffect, useRef, useState, type DragEvent } from 'react'
import {
  getRoomTypeImages,
  reorderRoomTypeImages,
  uploadRoomTypeImage,
} from '../services/roomTypeService'
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
  const [isSavingOrder, setIsSavingOrder] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [successNotice, setSuccessNotice] = useState<string | null>(null)
  const [isDraggingFile, setIsDraggingFile] = useState(false)
  const [previewImage, setPreviewImage] = useState<RoomTypeImage | null>(null)

  // Drag and drop sắp xếp thứ tự ảnh
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null)
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null)

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
    setSuccessNotice(null)

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
      setSuccessNotice('Đã tải ảnh lên thành công.')
      setTimeout(() => setSuccessNotice(null), 3000)
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
    event.target.value = ''
  }

  function handleDropzoneDragOver(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    if (!isUploading && images.length < MAX_IMAGES && canManage && draggedIndex === null) {
      setIsDraggingFile(true)
    }
  }

  function handleDropzoneDragLeave(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setIsDraggingFile(false)
  }

  function handleDropzoneDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setIsDraggingFile(false)
    if (!canManage || isUploading || images.length >= MAX_IMAGES || draggedIndex !== null) return

    const file = e.dataTransfer.files?.[0]
    if (file) {
      void handleFileProcess(file)
    }
  }

  // S2-09: Kéo thả các thẻ ảnh để sắp xếp lại thứ tự
  async function applyReorder(reorderedList: RoomTypeImage[]) {
    // Quy tắc hiển thị: ảnh nằm ở vị trí đầu tiên sau khi sắp xếp tự động trở thành ảnh đại diện mới
    const updated = reorderedList.map((img, i) => ({
      ...img,
      displayOrder: i,
      isPrimary: i === 0,
    }))

    setImages(updated)
    setDraggedIndex(null)
    setDragOverIndex(null)

    try {
      setIsSavingOrder(true)
      setError(null)
      const saved = await reorderRoomTypeImages(
        roomType.id,
        updated.map((img) => img.id),
      )
      setImages(saved)
      setSuccessNotice('Đã cập nhật thứ tự ảnh. Ảnh đầu tiên đã chuyển thành ảnh đại diện mới.')
      setTimeout(() => setSuccessNotice(null), 3500)
      onImagesUpdated()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không lưu được thứ tự ảnh mới.')
      void fetchImages()
    } finally {
      setIsSavingOrder(false)
    }
  }

  function handleCardDragStart(e: DragEvent<HTMLDivElement>, index: number) {
    if (!canManage || isSavingOrder) return
    setDraggedIndex(index)
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(index))
  }

  function handleCardDragOver(e: DragEvent<HTMLDivElement>, index: number) {
    if (!canManage || draggedIndex === null) return
    e.preventDefault()
    e.dataTransfer.dropEffect = 'move'
    if (dragOverIndex !== index) {
      setDragOverIndex(index)
    }
  }

  function handleCardDrop(e: DragEvent<HTMLDivElement>, targetIndex: number) {
    e.preventDefault()
    if (!canManage || draggedIndex === null || draggedIndex === targetIndex) {
      setDraggedIndex(null)
      setDragOverIndex(null)
      return
    }

    const reordered = [...images]
    const [moved] = reordered.splice(draggedIndex, 1)
    reordered.splice(targetIndex, 0, moved)

    void applyReorder(reordered)
  }

  function handleCardDragEnd() {
    setDraggedIndex(null)
    setDragOverIndex(null)
  }

  // Thao tác nhanh chuyển vị trí (hỗ trợ thêm cho khả năng tiếp cận và tiện ích)
  function moveCard(fromIndex: number, toIndex: number) {
    if (toIndex < 0 || toIndex >= images.length) return
    const reordered = [...images]
    const [moved] = reordered.splice(fromIndex, 1)
    reordered.splice(toIndex, 0, moved)
    void applyReorder(reordered)
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
            <p>
              Tải ảnh lên, kéo thả sắp xếp thứ tự trực quan. Ảnh ở vị trí đầu tiên sẽ tự động làm ảnh đại diện.
            </p>
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
              <span className="room-type-image-badge-item">Tối đa 8 ảnh</span>
              <span className="room-type-image-badge-item">Kéo thả đổi thứ tự</span>
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

          {successNotice && (
            <div className="room-type-image-reorder-notice" role="status">
              <span>✓</span>
              <span>{successNotice}</span>
            </div>
          )}

          {canManage && (
            <div
              className={`room-type-upload-zone ${isDraggingFile ? 'dragging' : ''} ${
                isFull || isUploading ? 'disabled' : ''
              }`}
              onDragOver={handleDropzoneDragOver}
              onDragLeave={handleDropzoneDragLeave}
              onDrop={handleDropzoneDrop}
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
                    Hỗ trợ JPG, PNG dưới 5MB. Kéo thả các ảnh bên dưới để thay đổi thứ tự hiển thị.
                  </p>
                </div>
              )}
            </div>
          )}

          <div>
            <div className="room-type-image-grid-title">
              <span>
                Danh sách ảnh ({images.length}) {isSavingOrder && '— Đang lưu thứ tự mới...'}
              </span>
              <span className="room-type-image-grid-hint">
                {canManage ? '💡 Kéo thả thẻ ảnh để đổi thứ tự, ảnh đầu tiên là ảnh đại diện' : 'Ảnh đầu tiên là ảnh đại diện'}
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
                  const isBeingDragged = draggedIndex === index
                  const isBeingHoveredOver = dragOverIndex === index

                  return (
                    <div
                      key={img.id}
                      draggable={canManage && !isSavingOrder}
                      onDragStart={(e) => handleCardDragStart(e, index)}
                      onDragOver={(e) => handleCardDragOver(e, index)}
                      onDrop={(e) => handleCardDrop(e, index)}
                      onDragEnd={handleCardDragEnd}
                      className={`room-type-image-card ${canManage ? 'draggable' : ''} ${
                        isPrimary ? 'primary' : ''
                      } ${isBeingDragged ? 'dragging' : ''} ${
                        isBeingHoveredOver ? 'drag-over' : ''
                      }`}
                      onClick={() => setPreviewImage(img)}
                      title={
                        canManage
                          ? `Kéo thả để đổi vị trí (Ảnh #${index + 1}). Nhấn để phóng to.`
                          : 'Nhấn để phóng to'
                      }
                    >
                      <img
                        src={img.thumbnailUrl}
                        alt={`Ảnh loại phòng ${roomType.name} #${index + 1}`}
                        className="room-type-image-img"
                        loading="lazy"
                      />

                      {/* Nút cầm kéo thả */}
                      {canManage && (
                        <div
                          className="room-type-image-drag-handle"
                          title="Kéo thả để sắp xếp lại"
                          onClick={(e) => e.stopPropagation()}
                        >
                          ⋮⋮
                        </div>
                      )}

                      {/* Huy hiệu ảnh đại diện nổi bật */}
                      {isPrimary && (
                        <div className="room-type-image-primary-badge">
                          <span>★</span>
                          <span>Ảnh đại diện</span>
                        </div>
                      )}

                      {/* Huy hiệu thứ tự */}
                      <div className="room-type-image-order-badge">#{index + 1}</div>

                      {/* Thanh thao tác nhanh (hover) */}
                      {canManage && (
                        <div
                          className="room-type-image-card-actions"
                          onClick={(e) => e.stopPropagation()}
                        >
                          {index > 0 && (
                            <button
                              type="button"
                              className="room-type-image-quick-btn"
                              title="Đặt làm ảnh đại diện (chuyển lên đầu)"
                              onClick={() => moveCard(index, 0)}
                            >
                              ★ Làm đại diện
                            </button>
                          )}
                          {index > 0 && (
                            <button
                              type="button"
                              className="room-type-image-quick-btn"
                              title="Chuyển sang trái"
                              onClick={() => moveCard(index, index - 1)}
                            >
                              ◀
                            </button>
                          )}
                          {index < images.length - 1 && (
                            <button
                              type="button"
                              className="room-type-image-quick-btn"
                              title="Chuyển sang phải"
                              onClick={() => moveCard(index, index + 1)}
                            >
                              ▶
                            </button>
                          )}
                        </div>
                      )}
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
