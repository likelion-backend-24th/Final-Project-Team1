import { api } from './client'
import type { ApiResponse, CheckinResult, CheckinTicketView } from '../types'

// Crockford Base32 - 헷갈리는 I·L·O·U 가 빠져 있다(reservation-service 의 생성기와 같은 집합).
const RESERVATION_NO_PATTERN = /^R-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$/

export type CheckinMethod = 'QR' | 'RESERVATION_NO'

/** 입력이 R-XXXX-XXXX 면 예약번호, 아니면 QR 의 체크인 토큰. 조회와 이력이 같은 판단을 쓴다. */
export function checkinMethodOf(input: string): CheckinMethod {
  return RESERVATION_NO_PATTERN.test(input.trim().toUpperCase()) ? 'RESERVATION_NO' : 'QR'
}

export interface CheckinReport {
  expoId: number
  expoTitle: string
  reserved: number
  capacity: number
  checkedIn: number
  noShow: number
  checkinRate: number
  hourly: { hour: number; count: number }[]
  byMethod: Record<string, number>
  reverted: number
  /** LLM 요약. 실패했거나 입장 기록이 없으면 null 이고 숫자만 보여준다. */
  summary: string | null
}

export const ticketApi = {
  /** GET /api/v1/tickets/verify — 조회만 한다(미전이). 입력이 R-XXXX-XXXX 면 예약번호, 아니면 체크인 토큰. */
  verify: (input: string) => {
    const value = input.trim()
    const query = checkinMethodOf(value) === 'RESERVATION_NO'
      ? `reservationNo=${encodeURIComponent(value.toUpperCase())}`
      : `code=${encodeURIComponent(value)}`
    return api.get<ApiResponse<CheckinTicketView>>(`/tickets/verify?${query}`)
  },

  /** POST /api/v1/tickets/{ticketId}/checkin — 체크인 확정(USED 전이). method 는 이력용. */
  checkin: (ticketId: number, method?: CheckinMethod) =>
    api.post<ApiResponse<CheckinResult>>(
      `/tickets/${ticketId}/checkin${method ? `?method=${method}` : ''}`, {}),

  /** GET /api/v1/tickets/checkin-report — 주최자 전용. 체크인 결과 집계 + AI 요약. */
  checkinReport: (expoId: number) =>
    api.get<ApiResponse<CheckinReport>>(`/tickets/checkin-report?expoId=${expoId}`),

  /** POST /api/v1/tickets/{ticketId}/checkin/cancellation — 체크인 되돌리기(ISSUED 전이). */
  cancelCheckin: (ticketId: number) =>
    api.post<ApiResponse<CheckinResult>>(`/tickets/${ticketId}/checkin/cancellation`, {}),
}
