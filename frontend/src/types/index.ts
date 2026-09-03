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
  fee?: number // RoundResponse 에만 있다. RoundView 에는 없다.
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
