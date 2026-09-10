export interface User {
  id: number
  name: string
  email: string
  role: 'USER' | 'ORGANIZER' | 'SUPER_ADMIN'
}

/**
 * expo-service 의 ExpoSummaryResponse / ExpoDetailResponse / ExpoResponse 를 하나로 받는 타입.
 *
 * 주의: 목록·상세는 PK 를 expoId 로, 등록(createExpo)만 id 로 내려준다.
 * 둘 다 optional 로 두고 expoKey() 로 꺼내 쓴다.
 */
export interface Expo {
  expoId?: number // 목록(ExpoSummaryResponse) · 상세(ExpoDetailResponse)
  id?: number // 등록(ExpoResponse)
  channelId: number
  title: string
  description?: string
  category: string
  region?: string
  venue?: string
  thumbnailUrl?: string
  status?: 'HIDDEN' | 'PUBLISHED' | 'CLOSED' // 목록 응답에는 없다(항상 PUBLISHED)
  createdAt?: string

  // 상세(ExpoDetailResponse) 에만 있다.
  // roundsAvailable=false 는 reservation-service 호출이 실패했다는 뜻(부분 실패 허용).
  roundsAvailable?: boolean
  rounds?: Round[]
}

/** 응답마다 PK 필드명이 달라서 한 곳에서 흡수한다. */
export function expoKey(e: Expo): number {
  return (e.expoId ?? e.id) as number
}

/**
 * reservation-service 의 RoundResponse / expo-service 의 RoundView.
 * 필드명이 startsAt / endsAt 이다. startAt / endAt 아님.
 */
export interface Round {
  roundId: number
  startsAt: string
  endsAt: string
  capacity: number
  remaining: number
  fee: number
}

/**
 * reservation-service 의 ReservationResponse.
 * paymentId 는 무료 회차(amount=0, 즉시 CONFIRMED)면 응답에서 빠진다.
 */
export interface Reservation {
  reservationId: number
  reservationNo: string
  roundId: number
  headcount: number
  amount: number
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED'
  expiresAt?: string
  paymentId?: string
}

export type RefundState = 'NOT_APPLICABLE' | 'REFUNDED' | 'REFUND_PENDING' | 'REFUND_UNRESOLVED' | 'NOT_REFUNDABLE'

/** GET /reservations/me 한 줄. reservation-service 의 MyReservationResponse. */
export interface MyReservation {
  reservationId: number
  reservationNo: string
  expoId: number
  roundId: number
  startsAt?: string
  endsAt?: string
  headcount: number
  amount: number
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED'
  refundState: RefundState
  createdAt: string
}

/** GET /reservations/{id} 상세에 실리는 QR 정보. reservation-service 의 ReservationTicketView. */
export interface ReservationTicket {
  ticketId: number
  checkinToken: string
  issuedAt: string
  status: string
}

/** GET /reservations/{id}. reservation-service 의 MyReservationDetailResponse. */
export interface MyReservationDetail extends MyReservation {
  contactName: string
  contactPhone: string
  ticketAvailable: boolean
  ticket?: ReservationTicket
}

/** PATCH /reservations/{id}/cancellation 응답. reservation-service 의 CancelReservationResponse. */
export interface CancelReservationResult {
  reservationId: number
  status: string
  refundState: RefundState
  cancelledAt: string
}

/** GET /tickets/verify. ticket-service 의 CheckinTicketView. */
export interface CheckinTicketView {
  ticketId: number
  status: string
  roundId: number
  headcount: number
  issuedAt: string
  usedAt?: string
}

/** POST /tickets/{id}/checkin. ticket-service 의 CheckinResult. */
export interface CheckinResult {
  ticketId: number
  status: string
  checkedInAt: string
}

/** GET /expo-promotions/active. expo-service 의 ActivePromotionResponse. recommended 탭 상단 VIP 배너에 쓴다. */
export interface ActivePromotion {
  promotionId: number
  expoId: number
  title: string
  thumbnailUrl?: string
  region?: string
  category: string
  paidAt: string
}

export interface Channel {
  id: number
  name: string
  description?: string
  ownerId: number
  createdAt?: string
}

/** Spring Data Page 를 그대로 직렬화한 모양. GET /channels/my 가 이걸 준다. */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/** 전 서비스 공통 응답 봉투. 실패 시 data 는 { code } 만 담는다. */
export interface ApiResponse<T> {
  success: boolean
  data: T
  meta?: unknown
  message?: string
}
