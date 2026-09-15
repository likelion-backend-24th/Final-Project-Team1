import { api } from './client'
import type { ApiResponse, CheckinResult, CheckinTicketView } from '../types'

// Crockford Base32 - 헷갈리는 I·L·O·U 가 빠져 있다(reservation-service 의 생성기와 같은 집합).
const RESERVATION_NO_PATTERN = /^R-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$/

export const ticketApi = {
  /** GET /api/v1/tickets/verify — 조회만 한다(미전이). 입력이 R-XXXX-XXXX 면 예약번호, 아니면 체크인 토큰. */
  verify: (input: string) => {
    const value = input.trim()
    const query = RESERVATION_NO_PATTERN.test(value.toUpperCase())
      ? `reservationNo=${encodeURIComponent(value.toUpperCase())}`
      : `code=${encodeURIComponent(value)}`
    return api.get<ApiResponse<CheckinTicketView>>(`/tickets/verify?${query}`)
  },

  /** POST /api/v1/tickets/{ticketId}/checkin — 체크인 확정(USED 전이). */
  checkin: (ticketId: number) =>
    api.post<ApiResponse<CheckinResult>>(`/tickets/${ticketId}/checkin`, {}),
}
