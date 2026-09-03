import { api } from './client'
import type { ApiResponse, Expo, Channel } from '../types'

export const expoApi = {
  listPublished: (params?: { category?: string; region?: string; keyword?: string; page?: number }) => {
    const q = new URLSearchParams({ status: 'PUBLISHED' })
    if (params?.category) q.set('category', params.category)
    if (params?.region) q.set('region', params.region)
    if (params?.keyword) q.set('keyword', params.keyword)
    if (params?.page) q.set('page', String(params.page))
    return api.get<ApiResponse<Expo[]>>(`/expos?${q}`)
  },

  getExpo: (expoId: number) =>
    api.get<ApiResponse<Expo>>(`/expos/${expoId}`),

  createChannel: (data: { name: string; description?: string }) =>
    api.post<ApiResponse<Channel>>('/channels', data),

  listMyChannels: () =>
    api.get<ApiResponse<Channel[]>>('/channels/me'),

  createExpo: (channelId: number, data: {
    title: string
    description?: string
    category: string
    region?: string
    venue?: string
    thumbnailUrl?: string
  }) => api.post<ApiResponse<Expo>>(`/channels/${channelId}/expos`, data),

  publishExpo: (expoId: number) =>
    api.patch<ApiResponse<Expo>>(`/expos/${expoId}/publish`),

  listMyExpos: (channelId: number) =>
    api.get<ApiResponse<Expo[]>>(`/channels/${channelId}/expos`),
}
