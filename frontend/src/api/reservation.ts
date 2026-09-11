import { api } from './client'
import type { ApiResponse, CancelReservationResult, MyReservation, MyReservationDetail, Reservation } from '../types'

export const reservationApi = {
  /** GET /api/v1/reservations/me — 본인 예약 전체(페이징 없음). */
  listMine: () => api.get<ApiResponse<MyReservation[]>>('/reservations/me'),

  /** GET /api/v1/reservations/{id} — 연락처·QR 을 포함한 상세. */
  getMine: (reservationId: number) =>
    api.get<ApiResponse<MyReservationDetail>>(`/reservations/${reservationId}`),

  /**
   * PATCH /api/v1/reservations/{id}/cancellation
   * 이미 취소된 예약도 200(멱등) — 재시도가 오류로 보이지 않는다.
   */
  cancel: (reservationId: number) =>
    api.patch<ApiResponse<CancelReservationResult>>(`/reservations/${reservationId}/cancellation`),

  /**
   * POST /api/v1/rounds/{roundId}/reservations
   * 무료 회차(fee=0)는 응답이 바로 status=CONFIRMED 다 — 결제 확정을 따로 부를 필요 없다.
   * 유료 회차는 status=PENDING 이고, confirmPayment() 를 이어서 불러야 자리가 확정된다.
   */
  create: (
    roundId: number,
    data: { headcount: number; contactName: string; contactPhone: string }
  ) => api.post<ApiResponse<Reservation>>(`/rounds/${roundId}/reservations`, data),

  /**
   * POST /api/v1/reservations/{reservationId}/payment
   * PG 결제 승인 결과를 조회해 예약을 CONFIRMED 로 전이시킨다.
   * 응답이 UNKNOWN(DEPENDENCY_UNAVAILABLE, 503)이면 상태를 바꾸지 않는다 — 잠시 후 재시도해야 한다.
   */
  confirmPayment: (reservationId: number) =>
    api.post<ApiResponse<{ reservationId: number; status: Reservation['status']; confirmedAt?: string }>>(
      `/reservations/${reservationId}/payment`,
      {},
      // 여기서 /auth 로 튕기면 결제가 성공했다는 사실을 사용자가 못 본다. 화면에서 직접 안내한다.
      { authRedirect: false },
    ),
}
