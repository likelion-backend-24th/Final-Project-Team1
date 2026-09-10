import { api } from './client'
import type { ActivePromotion, ApiResponse, Expo, Channel, ReservationSummary } from '../types'

const API_BASE = '/api/v1'
function getToken() {
  return localStorage.getItem('token')
}

export interface PublicationResponse {
  expoId: number
  status: 'HIDDEN' | 'PUBLISHED' | 'CLOSED'
}

export type ExpoSort = 'recommended' | 'newest' | 'deadline'

export interface ApplyPromotionResponse {
  promotionId: number
  expoId: number
  amount: number
  paymentId: string
  status: string
}

export const expoApi = {
  /**
   * GET /api/v1/expos — PUBLISHED 만 내려온다. 인증 불필요.
   * 백엔드가 받는 파라미터는 region · category · sort · page(1부터) · size(최대 100).
   * keyword 검색은 Sprint 2 범위라 아직 없다.
   */
  listPublished: (params?: {
    category?: string
    region?: string
    sort?: ExpoSort
    page?: number
    size?: number
  }) => {
    const q = new URLSearchParams()
    if (params?.category) q.set('category', params.category)
    if (params?.region) q.set('region', params.region)
    if (params?.sort) q.set('sort', params.sort)
    q.set('page', String(params?.page ?? 1))
    q.set('size', String(params?.size ?? 100))
    return api.get<ApiResponse<Expo[]>>(`/expos?${q}`)
  },

  /**
   * GET /api/v1/expo-promotions/active — 인증 불필요.
   * recommended 탭 상단 VIP 배너용. 백엔드가 정렬에 섞어주지 않으므로 프론트가 별도로 불러 조합한다.
   */
  getActivePromotions: () => api.get<ApiResponse<ActivePromotion[]>>('/expo-promotions/active'),

  /**
   * GET /api/v1/expos/{expoId} — PUBLISHED 가 아니면 404 다.
   * 응답에 rounds · roundsAvailable 이 함께 들어온다
   * (expo-service 가 reservation-service 의 내부 API 를 호출해 병합한 결과).
   */
  getExpo: (expoId: number) => api.get<ApiResponse<Expo>>(`/expos/${expoId}`),

  // POST /api/v1/channels
  createChannel: (data: { name: string; description?: string }) =>
    api.post<ApiResponse<Channel>>('/channels', data),

  // GET /api/v1/channels/my — 채널이 없으면 404
  getMyChannel: () => api.get<ApiResponse<Channel>>('/channels/my'),

  // POST /api/v1/channels/{channelId}/expos — 생성 직후 상태는 HIDDEN
  createExpo: (
    channelId: number,
    data: {
      title: string
      description?: string
      category: string
      region?: string
      venue?: string
      thumbnailUrl?: string
    }
  ) => api.post<ApiResponse<Expo>>(`/channels/${channelId}/expos`, data),

  /**
   * POST /api/v1/expos/{expoId}/publication — PATCH /publish 아님.
   * 회차가 하나도 없으면 400, 이미 공개면 멱등 200, 종료된 박람회는 409.
   * reservation-service 가 죽어 있으면 503 이고 상태는 그대로 유지된다.
   */
  publishExpo: (expoId: number) =>
    api.post<ApiResponse<PublicationResponse>>(`/expos/${expoId}/publication`, {}),

  // POST /api/v1/expo-promotions — paymentId 발급 (결제 전 단계)
  applyPromotion: (expoId: number) =>
    api.post<ApiResponse<ApplyPromotionResponse>>('/expo-promotions', { expoId }),

  // POST /api/v1/expo-promotions/{promotionId}/refund
  refundPromotion: (promotionId: number) =>
    api.post<ApiResponse<void>>(`/expo-promotions/${promotionId}/refund`, {}),

  // GET /api/v1/expos/{expoId}/reservations/summary — 주최자(채널 소유자) 전용.
  getReservationSummary: (expoId: number) =>
    api.get<ApiResponse<ReservationSummary>>(`/expos/${expoId}/reservations/summary`),

  /**
   * GET /api/v1/expos/{expoId}/reservations/attendees.xlsx — 주최자 전용, 바이너리 응답.
   * apiFetch(JSON 파싱 전제)를 쓸 수 없어 fetch 를 직접 호출해 blob 으로 받고 다운로드를 트리거한다.
   * roundId 를 생략하면 박람회 전체 명단이 내려온다.
   */
  downloadAttendeesExcel: async (expoId: number, roundId?: number) => {
    const q = roundId ? `?roundId=${roundId}` : ''
    const token = getToken()
    const res = await fetch(`${API_BASE}/expos/${expoId}/reservations/attendees.xlsx${q}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      throw Object.assign(new Error('download failed'), { status: res.status, body })
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'attendees.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  },
}

// 주최자용 "내 채널의 박람회 목록" 엔드포인트는 아직 없다(Sprint 2).
// HostChannelPage 는 공개 목록을 channelId 로 걸러서 대신 보여준다.
