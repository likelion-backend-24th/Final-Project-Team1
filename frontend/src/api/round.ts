import { api } from './client'
import type { ApiResponse, Round } from '../types'

export const roundApi = {
  /**
   * GET /api/v1/expos/{expoId}/rounds — reservation-service.
   * ORGANIZER 전용이며 해당 박람회 채널의 소유자만 볼 수 있다(그 외 401/403).
   * 방문자용 회차 목록은 이 API 가 아니라 GET /expos/{id} 의 rounds 를 쓴다.
   */
  listByExpo: (expoId: number) =>
    api.get<ApiResponse<Round[]>>(`/expos/${expoId}/rounds`),

  /**
   * POST /api/v1/expos/{expoId}/rounds
   * 필드명은 startsAt · endsAt · fee 다. startAt · endAt · price 아님.
   * startsAt 은 미래여야 하고 endsAt > startsAt, capacity >= 1, fee >= 0.
   */
  createRound: (
    expoId: number,
    data: {
      startsAt: string // ISO-8601 UTC
      endsAt: string
      capacity: number
      fee?: number
    }
  ) => api.post<ApiResponse<Round>>(`/expos/${expoId}/rounds`, data),
}

// 회차 삭제(DELETE /expos/{expoId}/rounds/{roundId}) 는 Sprint 1 범위가 아니다.
// 예약이 걸린 회차의 삭제 정책이 정해져야 하므로 Sprint 2 에서 추가한다.
