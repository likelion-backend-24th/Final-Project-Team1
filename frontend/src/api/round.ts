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

  /**
   * PATCH /api/v1/expos/{expoId}/rounds/{roundId}
   * 네 값을 모두 보낸다 - 서버가 조건부 UPDATE 로 통째로 덮어쓴다.
   * 활성 예약이 0건이고 아직 시작하지 않은 회차만 바꿀 수 있다(그 외 409).
   */
  updateRound: (
    expoId: number,
    roundId: number,
    data: {
      startsAt: string // ISO-8601 UTC
      endsAt: string
      capacity: number
      fee: number
    }
  ) => api.patch<ApiResponse<Round>>(`/expos/${expoId}/rounds/${roundId}`, data),

  /**
   * DELETE /api/v1/expos/{expoId}/rounds/{roundId} — 소프트 삭제. 204.
   * 마지막 살아있는 회차면 서버가 박람회를 먼저 비공개로 바꾼 뒤 지운다.
   */
  deleteRound: (expoId: number, roundId: number) =>
    api.delete<void>(`/expos/${expoId}/rounds/${roundId}`),
}
