import { api } from './client'
import type { ApiResponse } from '../types'

export type NotificationType = 'RECOMMENDATION' | 'RESERVATION_CONFIRMED'

export interface NotificationItem {
  id: number
  expoId: number
  type: NotificationType
  message: string
  isRead: boolean
  createdAt: string // UTC ISO-8601
}

export interface NotificationList {
  notifications: NotificationItem[]
  hasNext: boolean
}

// 알림은 부가 기능이다. 추천 서비스가 죽었거나 토큰이 애매해도 로그인 화면으로 튕기지 않는다.
const quiet = { authRedirect: false }

export const notificationApi = {
  // GET /api/v1/me/notifications/unread-count
  unreadCount: () =>
    api.get<ApiResponse<{ unreadCount: number }>>('/me/notifications/unread-count', quiet),
  // GET /api/v1/me/notifications
  list: (size = 10) =>
    api.get<ApiResponse<NotificationList>>(`/me/notifications?page=0&size=${size}`, quiet),
  // PATCH /api/v1/me/notifications/{id}/read
  markRead: (id: number) => api.patch<ApiResponse<null>>(`/me/notifications/${id}/read`, undefined, quiet),
  // PATCH /api/v1/me/notifications/read-all
  markAllRead: () => api.patch<ApiResponse<null>>('/me/notifications/read-all', undefined, quiet),
}

const REFRESH_EVENT = 'expohub:notifications-refresh'

/**
 * 알림이 곧 생길 일(예약 확정 등)을 마친 화면이 부른다. 헤더 종 아이콘이 개수를 다시 읽는다.
 * 서버는 커밋 뒤 비동기로 알림을 만들기 때문에 받는 쪽에서 잠깐 기다렸다가 읽는다.
 */
export function requestNotificationRefresh() {
  window.dispatchEvent(new Event(REFRESH_EVENT))
}

export function onNotificationRefresh(listener: () => void) {
  window.addEventListener(REFRESH_EVENT, listener)
  return () => window.removeEventListener(REFRESH_EVENT, listener)
}
