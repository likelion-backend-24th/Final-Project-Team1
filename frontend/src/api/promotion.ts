import { api } from './client'
import type { ApiResponse } from '../types'

export interface ActivePromotion {
  promotionId: number
  expoId: number
  title: string
  category: string
  region?: string
  thumbnailUrl?: string | null
  paidAt: string
}

export interface ApplyPromotionResponse {
  promotionId: number
  expoId: number
  amount: number
  paymentId: string
  status: string
}

export const promotionApi = {
  getActive: () =>
    api.get<ApiResponse<ActivePromotion[]>>('/expo-promotions/active'),

  apply: (expoId: number) =>
    api.post<ApiResponse<ApplyPromotionResponse>>('/expo-promotions', { expoId }),

  refund: (promotionId: number) =>
    api.post<ApiResponse<void>>(`/expo-promotions/${promotionId}/refund`, {}),
}
