import { api } from './client'

export type SettlementPeriodType = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR'

export interface SettlementBucket {
  label: string
  revenue: number
  refund: number
  net: number
}

export interface ExpoRanking {
  expoId: number
  title: string
  revenue: number
}

export interface CategoryRanking {
  category: string
  revenue: number
}

/**
 * settlement-service 의 GET /api/v1/admin/settlement 응답.
 * 다른 서비스와 달리 ApiResponse 봉투를 안 씌우고 그대로 내려온다.
 */
export interface AdminSettlementResponse {
  period: SettlementPeriodType
  from: string
  to: string
  totalRevenue: number
  totalRefund: number
  netRevenue: number
  platformFee: number
  feeRate: number
  reservationRevenue: number
  reservationRefund: number
  promotionRevenue: number
  promotionRefund: number
  reservationPaidCount: number
  reservationRefundCount: number
  promotionPaidCount: number
  promotionRefundCount: number
  buckets: SettlementBucket[]
  topExpos: ExpoRanking[]
  topCategories: CategoryRanking[]
}

export const settlementApi = {
  // GET /api/v1/admin/settlement?period=&date=  (SUPER_ADMIN 전용)
  // date 는 "YYYY-MM-DD" - 그 기간(day/week/month/year)을 잡는 기준일이다.
  getSettlement: (period: SettlementPeriodType, date: string) =>
    api.get<AdminSettlementResponse>(`/admin/settlement?period=${period}&date=${date}`),
}
