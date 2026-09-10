import { api } from './client'
import type { ApiResponse, CheckinResult, CheckinTicketView } from '../types'

export const ticketApi = {
  /** GET /api/v1/tickets/verify?code= — QR 의 체크인 토큰으로 조회만 한다(미전이). */
  verify: (code: string) =>
    api.get<ApiResponse<CheckinTicketView>>(`/tickets/verify?code=${encodeURIComponent(code)}`),

  /** POST /api/v1/tickets/{ticketId}/checkin — 체크인 확정(USED 전이). */
  checkin: (ticketId: number) =>
    api.post<ApiResponse<CheckinResult>>(`/tickets/${ticketId}/checkin`, {}),
}
