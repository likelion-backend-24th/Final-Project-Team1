export interface User {
  id: number
  name: string
  email: string
  role: 'USER' | 'ORGANIZER' | 'SUPER_ADMIN'
}

export interface Expo {
  id: number
  channelId: number
  channelName?: string
  title: string
  description?: string
  category: string
  region?: string
  venue?: string
  thumbnailUrl?: string
  status: 'HIDDEN' | 'PUBLISHED' | 'CLOSED'
  createdAt: string
}

export interface Round {
  id: number
  expoId: number
  startAt: string
  endAt: string
  capacity: number
  remaining: number
}

export interface Channel {
  id: number
  name: string
  description?: string
  ownerId: number
}

export interface HostRequest {
  id: number
  userId: number
  userName: string
  userEmail: string
  orgName: string
  contactName: string
  contactPhone: string
  purpose?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  createdAt: string
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}
