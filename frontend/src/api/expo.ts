import { api } from './client'
import type { ApiResponse, Expo, Channel, Page } from '../types'

export interface PublicationResponse {
  expoId: number
  status: 'HIDDEN' | 'PUBLISHED' | 'CLOSED'
}

export const expoApi = {
  /**
   * GET /api/v1/expos — PUBLISHED 만 내려온다. 인증 불필요.
   * 백엔드가 받는 파라미터는 region · category · page(1부터) · size(최대 100) 뿐이다.
   * keyword 검색은 Sprint 2 범위라 아직 없다.
   */
  listPublished: (params?: {
    category?: string
    region?: string
    page?: number
    size?: number
  }) => {
    const q = new URLSearchParams()
    if (params?.category) q.set('category', params.category)
    if (params?.region) q.set('region', params.region)
    q.set('page', String(params?.page ?? 1))
    q.set('size', String(params?.size ?? 100))
    return api.get<ApiResponse<Expo[]>>(`/expos?${q}`)
  },

  /**
   * GET /api/v1/expos/{expoId} — PUBLISHED 가 아니면 404 다.
   * 응답에 rounds · roundsAvailable 이 함께 들어온다
   * (expo-service 가 reservation-service 의 내부 API 를 호출해 병합한 결과).
   */
  getExpo: (expoId: number) => api.get<ApiResponse<Expo>>(`/expos/${expoId}`),

  // POST /api/v1/channels
  createChannel: (data: { name: string; description?: string }) =>
    api.post<ApiResponse<Channel>>('/channels', data),

  // GET /api/v1/channels/my — 경로가 /me 가 아니라 /my 이고, Page 로 감싸여 온다.
  listMyChannels: () => api.get<ApiResponse<Page<Channel>>>('/channels/my'),

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
}

// 주최자용 "내 채널의 박람회 목록" 엔드포인트는 아직 없다(Sprint 2).
// HostChannelPage 는 공개 목록을 channelId 로 걸러서 대신 보여준다.
