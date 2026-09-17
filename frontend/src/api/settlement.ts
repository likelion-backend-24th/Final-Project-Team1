import { api } from './client'

/**
 * settlement-service 의 GET /api/v1/admin/settlement 응답.
 * 다른 서비스와 달리 ApiResponse 봉투를 안 씌우고 그대로 내려온다.
 */
export interface AdminSettlementResponse {
  year: number
  month: number | null
  totalRevenue: number
  totalRefund: number
  netRevenue: number
  platformFee: number
  feeRate: number
  reservationRevenue: number
  reservationRefund: number
  promotionRevenue: number
  promotionRefund: number
}

export const settlementApi = {
  // GET /api/v1/admin/settlement?year=&month=  (SUPER_ADMIN 전용)
  // month 를 생략하면 해당 연도 전체를 집계한 연간 정산으로 조회된다.
  getSettlement: (year: number, month?: number) =>
    api.get<AdminSettlementResponse>(`/admin/settlement?year=${year}${month != null ? `&month=${month}` : ''}`),
}
