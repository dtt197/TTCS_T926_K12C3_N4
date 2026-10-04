import { useCallback, useEffect, useRef, useState, type DragEvent } from 'react'
import {
  deleteRoomTypeImage,
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
  const draggedIndexRef = useRef<number | null>(null)
  const isDraggingRef = useRef(false)

  // S2-09: Xoá ảnh có hộp thoại xác nhận và bảo vệ ảnh cuối cùng của phòng đang bán
  const [imageToDelete, setImageToDelete] = useState<RoomTypeImage | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

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
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void fetchImages()
  }, [fetchImages])
  

  // Điều kiện bảo vệ: phòng đang mở bán và chỉ còn 1 ảnh duy nhất
  const isLastImageOfActiveRoom = roomType.active && images.length <= 1

  function handleRequestDelete(img: RoomTypeImage) {
    if (isLastImageOfActiveRoom) {
      setError(
        'Không được phép xoá ảnh cuối cùng của loại phòng đang mở bán. Vui lòng ngừng bán loại phòng trước khi xoá ảnh này.'
      )
      return
    }
    setDeleteError(null)
    setImageToDelete(img)
  }

  async function handleConfirmDelete() {
    if (!imageToDelete) return

    try {
      setIsDeleting(true)
      setDeleteError(null)
      const updated = await deleteRoomTypeImage(roomType.id, imageToDelete.id)
      setImages(updated)
      setImageToDelete(null)
      setSuccessNotice('Đã xoá ảnh thành công. Ảnh đại diện và thứ tự hiển thị đã được cập nhật tự động.')
      setTimeout(() => setSuccessNotice(null), 3500)
      onImagesUpdated()
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : 'Xoá ảnh thất bại.')
    } finally {
      setIsDeleting(false)
    }
  }

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
    if (!canManage || isSavingOrder || isDeleting) return
    draggedIndexRef.current = index
    isDraggingRef.current = true
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(index))
    // Sử dụng setTimeout 0 để không gây gián đoạn quá trình DragStart của trình duyệt
    setTimeout(() => {
      setDraggedIndex(index)
    }, 0)
  }

  function handleCardDragOver(e: DragEvent<HTMLDivElement>, index: number) {
    if (!canManage || isSavingOrder || isDeleting) return
    e.preventDefault() // BẮT BUỘC để cho phép thả (drop)
    e.stopPropagation()
    e.dataTransfer.dropEffect = 'move'
    if (dragOverIndex !== index) {
      setDragOverIndex(index)
    }
  }

  function handleCardDragEnter(e: DragEvent<HTMLDivElement>, index: number) {
    if (!canManage || isSavingOrder || isDeleting) return
    e.preventDefault()
    e.stopPropagation()
    if (dragOverIndex !== index) {
      setDragOverIndex(index)
    }
  }

  function handleCardDragLeave(e: DragEvent<HTMLDivElement>) {
    e.stopPropagation()
  }

  function handleCardDrop(e: DragEvent<HTMLDivElement>, targetIndex: number) {
    e.preventDefault()
    e.stopPropagation()

    let fromIndex = draggedIndexRef.current
    if (fromIndex === null) {
      const raw = e.dataTransfer.getData('text/plain')
      if (raw !== '') {
        const parsed = parseInt(raw, 10)
        if (!isNaN(parsed)) {
          fromIndex = parsed
        }
      }
    }

    setDraggedIndex(null)
    setDragOverIndex(null)
    draggedIndexRef.current = null

    if (
      fromIndex === null ||
      fromIndex === targetIndex ||
      fromIndex < 0 ||
      fromIndex >= images.length
    ) {
      return
    }

    const reordered = [...images]
    const [moved] = reordered.splice(fromIndex, 1)
    reordered.splice(targetIndex, 0, moved)

    void applyReorder(reordered)
  }

  function handleCardDragEnd() {
    setDraggedIndex(null)
    setDragOverIndex(null)
    draggedIndexRef.current = null
    setTimeout(() => {
      isDraggingRef.current = false
    }, 150)
  }

  // Thao tác nhanh chuyển vị trí (hỗ trợ thêm nút bấm mũi tên và làm đại diện)
  function moveCard(fromIndex: number, toIndex: number) {
    if (toIndex < 0 || toIndex >= images.length || fromIndex === toIndex || isSavingOrder) return
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
              Tải ảnh lên, kéo thả sắp xếp thứ tự trực quan và xoá ảnh có xác nhận.
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
              <span className="room-type-image-badge-item">Xoá có xác nhận</span>
            </div>
            <div className="room-type-image-status-group">
              <span className={`room-type-status-tag ${roomType.active ? 'active' : 'inactive'}`}>
                {roomType.active ? '● Đang mở bán' : '○ Ngừng bán'}
              </span>
              <span className={`room-type-image-count-badge ${isFull ? 'full' : ''}`}>
                {images.length} / {MAX_IMAGES} ảnh
              </span>
            </div>
          </div>

          {/* Cảnh báo bảo vệ ảnh cuối cùng của phòng đang mở bán */}
          {isLastImageOfActiveRoom && (
            <div className="room-type-image-protect-notice" role="status">
              <span className="protect-icon">🛡️</span>
              <div>
                <strong>Bảo vệ ảnh mở bán:</strong> Loại phòng đang trong trạng thái <em>Đang mở bán</em> và chỉ còn đúng 1 ảnh duy nhất. Hệ thống không cho phép xoá ảnh này để đảm bảo khách đặt phòng luôn thấy hình ảnh phòng.
              </div>
            </div>
          )}

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
                {canManage
                  ? '💡 Kéo thả để đổi thứ tự | Nút 🗑️ để xoá ảnh có xác nhận'
                  : 'Ảnh đầu tiên là ảnh đại diện'}
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
                      draggable={canManage && !isSavingOrder && !isDeleting}
                      onDragStart={(e) => handleCardDragStart(e, index)}
                      onDragOver={(e) => handleCardDragOver(e, index)}
                      onDragEnter={(e) => handleCardDragEnter(e, index)}
                      onDragLeave={handleCardDragLeave}
                      onDrop={(e) => handleCardDrop(e, index)}
                      onDragEnd={handleCardDragEnd}
                      className={`room-type-image-card ${canManage ? 'draggable' : ''} ${
                        isPrimary ? 'primary' : ''
                      } ${isBeingDragged ? 'dragging' : ''} ${
                        isBeingHoveredOver ? 'drag-over' : ''
                      }`}
                      onClick={() => {
                        if (!isDraggingRef.current) {
                          setPreviewImage(img)
                        }
                      }}
                      title={
                        canManage
                          ? `Ảnh #${index + 1}. Kéo thả hoặc bấm nút điều hướng bên dưới để đổi vị trí.`
                          : 'Nhấn để phóng to'
                      }
                    >
                      <img
                        src={img.thumbnailUrl}
                        alt={`Ảnh loại phòng ${roomType.name} #${index + 1}`}
                        className="room-type-image-img"
                        loading="lazy"
                        draggable={false}
                      />

                      {/* Huy hiệu ảnh đại diện / thứ tự ở góc trên bên trái */}
                      {isPrimary ? (
                        <div className="room-type-image-primary-badge">
                          <span>★</span>
                          <span>Đại diện (#1)</span>
                        </div>
                      ) : (
                        <div className="room-type-image-order-badge-top">
                          #{index + 1}
                        </div>
                      )}

                      {/* Nút cầm kéo thả */}
                      {canManage && (
                        <div
                          className="room-type-image-drag-handle"
                          title="Cầm vào đây hoặc thẻ ảnh để kéo thả đổi vị trí"
                        >
                          ⋮⋮
                        </div>
                      )}

                      {/* Nút xoá ảnh có xác nhận (S2-09) */}
                      {canManage && (
                        <button
                          type="button"
                          className={`room-type-image-delete-btn ${isLastImageOfActiveRoom ? 'protected' : ''}`}
                          title={
                            isLastImageOfActiveRoom
                              ? 'Không thể xoá ảnh cuối cùng của loại phòng đang mở bán'
                              : 'Xoá ảnh này (yêu cầu xác nhận)'
                          }
                          aria-label={`Xoá ảnh #${index + 1}`}
                          disabled={isSavingOrder || isDeleting}
                          onMouseDown={(e) => e.stopPropagation()}
                          onClick={(e) => {
                            e.stopPropagation()
                            handleRequestDelete(img)
                          }}
                        >
                          {isLastImageOfActiveRoom ? '🔒' : '🗑️'}
                        </button>
                      )}

                      {/* Thanh điều hướng đổi vị trí & làm đại diện (Luôn hiển thị trên từng thẻ ảnh) */}
                      {canManage && (
                        <div
                          className="room-type-image-card-controls"
                          onMouseDown={(e) => e.stopPropagation()}
                          onClick={(e) => e.stopPropagation()}
                        >
                          {index > 0 && (
                            <button
                              type="button"
                              className="room-type-image-ctrl-btn move-prev"
                              title="Chuyển ảnh này lên trước (đổi vị trí)"
                              disabled={isSavingOrder}
                              onClick={() => moveCard(index, index - 1)}
                            >
                              ◀ Trước
                            </button>
                          )}
                          {index < images.length - 1 && (
                            <button
                              type="button"
                              className="room-type-image-ctrl-btn move-next"
                              title="Chuyển ảnh này ra sau (đổi vị trí)"
                              disabled={isSavingOrder}
                              onClick={() => moveCard(index, index + 1)}
                            >
                              Sau ▶
                            </button>
                          )}
                          {index > 0 && (
                            <button
                              type="button"
                              className="room-type-image-ctrl-btn set-primary"
                              title="Đặt ảnh này làm ảnh đại diện mới"
                              disabled={isSavingOrder}
                              onClick={() => moveCard(index, 0)}
                            >
                              ★ Làm đại diện
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

      {/* S2-09: Hộp thoại xác nhận xoá ảnh */}
      {imageToDelete && (
        <div
          className="room-type-confirm-backdrop"
          onClick={() => {
            if (!isDeleting) {
              setImageToDelete(null)
              setDeleteError(null)
            }
          }}
        >
          <div
            className="room-type-confirm-dialog"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="room-type-confirm-title"
            aria-describedby="room-type-confirm-desc"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="room-type-confirm-header">
              <div className="room-type-confirm-icon-box">🗑️</div>
              <div>
                <h3 id="room-type-confirm-title" className="room-type-confirm-title">
                  Xác nhận xoá ảnh
                </h3>
                <p id="room-type-confirm-desc" className="room-type-confirm-desc">
                  Bạn có chắc chắn muốn xoá ảnh này khỏi loại phòng <strong>{roomType.name}</strong>?
                </p>
              </div>
            </div>

            <div className="room-type-confirm-body">
              <div className="room-type-confirm-preview-wrap">
                <img
                  src={imageToDelete.thumbnailUrl}
                  alt="Ảnh chuẩn bị xoá"
                  className="room-type-confirm-preview-img"
                />
                <div className="room-type-confirm-preview-details">
                  <div className="room-type-confirm-meta">
                    <span className="room-type-confirm-badge">Vị trí #{imageToDelete.displayOrder + 1}</span>
                    {imageToDelete.isPrimary && (
                      <span className="room-type-confirm-primary-badge">★ Ảnh đại diện hiện tại</span>
                    )}
                  </div>
                  {imageToDelete.isPrimary && images.length > 1 && (
                    <p className="room-type-confirm-note">
                      💡 Khi xoá ảnh đại diện này, ảnh kế tiếp sẽ tự động được chọn làm <strong>ảnh đại diện mới</strong>.
                    </p>
                  )}
                  <p className="room-type-confirm-warning">
                    ⚠️ Tệp ảnh gốc và bản thu nhỏ sẽ bị xoá vĩnh viễn khỏi máy chủ. Hành động này không thể hoàn tác.
                  </p>
                </div>
              </div>

              {deleteError && (
                <div className="room-type-image-error" role="alert" style={{ marginTop: '1rem' }}>
                  <span>⚠️</span>
                  <span>{deleteError}</span>
                </div>
              )}
            </div>

            <div className="room-type-confirm-footer">
              <button
                type="button"
                className="room-type-confirm-cancel-btn"
                disabled={isDeleting}
                onClick={() => {
                  setImageToDelete(null)
                  setDeleteError(null)
                }}
              >
                Huỷ bỏ
              </button>
              <button
                type="button"
                className="room-type-confirm-delete-btn"
                disabled={isDeleting}
                onClick={handleConfirmDelete}
              >
                {isDeleting ? 'Đang xoá...' : 'Đồng ý xoá'}
              </button>
            </div>
          </div>
        </div>
      )}

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
