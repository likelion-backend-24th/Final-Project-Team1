import { api } from './client'

/**
 * settlement-service 의 GET /api/v1/admin/settlement 응답.
 * 다른 서비스와 달리 ApiResponse 봉투를 안 씌우고 그대로 내려온다.
 */
export interface AdminSettlementResponse {
  year: number
  month: number
  totalRevenue: number
  totalRefund: number
  netRevenue: number
  platformFee: number
  feeRate: number
}

export const settlementApi = {
  // GET /api/v1/admin/settlement?year=&month=  (SUPER_ADMIN 전용)
  getSettlement: (year: number, month: number) =>
    api.get<AdminSettlementResponse>(`/admin/settlement?year=${year}&month=${month}`),
}
