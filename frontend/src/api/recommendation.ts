import { api } from './client'
import type { ApiResponse } from '../types'

export interface RecommendationItem {
  expoId: number
  title: string
  matchedTags: string[]
  score: number
}

export interface RecommendationResponse {
  recommendations: RecommendationItem[]
  generatedAt: string | null
}

export interface Interests {
  categories: string[]
  keywords: string[]
}

const quiet = { authRedirect: false }

export const recommendationApi = {
  getRecommendations: (size = 10) =>
    api.get<ApiResponse<RecommendationResponse>>(`/me/recommendations?size=${size}`, quiet),

  getInterests: () =>
    api.get<ApiResponse<Interests>>('/me/interests', quiet),

  saveInterests: (data: Interests) =>
    api.put<ApiResponse<Interests>>('/me/interests', data),

  getSimilar: (expoId: number) =>
    api.get<ApiResponse<{ expoIds: number[] }>>(`/expos/${expoId}/similar`, quiet),

  getTags: (expoId: number) =>
    api.get<ApiResponse<{ tags: string[] }>>(`/expos/${expoId}/tags`, quiet),
}
