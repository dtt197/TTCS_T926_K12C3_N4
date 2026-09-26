export type MaintenanceDraft = {
  reason: string
  startDate: string
  endDate: string
}
export type RoomStatus =
  | 'TRONG_SACH'
  | 'TRONG_BAN'
  | 'DANG_O'
  | 'BAO_TRI'

export type Room = {
  id: number
  roomNumber: string
  floor: number
  roomType: string
  status: RoomStatus
  active: boolean
  maintenanceReason: string | null
  maintenanceStartDate: string | null
  maintenanceEndDate: string | null
}

export type RoomStatusHistory = {
  id: number
  roomId: number
  roomNumber: string
  previousStatus: RoomStatus
  newStatus: RoomStatus
  changedBy: string
  changedAt: string
  maintenanceReason?: string | null
  maintenanceStartDate?: string | null
  maintenanceEndDate?: string | null
}

export const ROOM_STATUS_LABELS: Record<RoomStatus, string> = {
  TRONG_SACH: 'Trống sạch',
  TRONG_BAN: 'Trống bẩn',
  DANG_O: 'Đang ở',
  BAO_TRI: 'Bảo trì',
}

export const ROOM_STATUS_HELP: Record<RoomStatus, string> = {
  TRONG_SACH: 'Sẵn sàng nhận khách',
  TRONG_BAN: 'Cần buồng phòng dọn',
  DANG_O: 'Đang có khách lưu trú',
  BAO_TRI: 'Tạm ngưng bán',
}
