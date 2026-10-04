import type { PublicRoomTypeCard } from '../types/publicRoomType'

const API_BASE_URL = `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}/api`

export async function getPublicRoomTypeCards(): Promise<PublicRoomTypeCard[]> {
  const response = await fetch(`${API_BASE_URL}/public/room-type-cards`)

  if (!response.ok) {
    throw new Error(`Không tải được danh sách loại phòng (${response.status})`)
  }

  return (await response.json()) as PublicRoomTypeCard[]
}