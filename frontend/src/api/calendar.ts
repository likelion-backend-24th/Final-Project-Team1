import { api } from './client'
import type { ApiResponse } from '../types'

export type ConstraintSource = 'GEMINI' | 'RULE' | 'NONE'

export interface CalendarRoundView {
  roundId: number
  expoId: number
  expoTitle: string
  sequence: number
  startsAt: string
  endsAt: string
}

export interface CalendarSuggestMeta {
  constraintSource: ConstraintSource
  candidateCount: number
}

const quiet = { authRedirect: false }

export const calendarApi = {
  /**
   * GET /api/v1/calendar/events — 인증 불필요. 그 기간에 공개된 회차를 겹침 제거 없이 다 보여준다.
   * 캘린더 기본 화면(브라우징용)이고, AI 추천(suggest)은 이 위에 얹는 별도 동작이다.
   */
  listEvents: (from: string, to: string) => {
    const q = new URLSearchParams({ from, to })
    return api.get<ApiResponse<CalendarRoundView[]>>(`/calendar/events?${q}`, quiet)
  },

  /**
   * GET /api/v1/me/calendar/suggest — USER 전용. from·to 는 UTC ISO 문자열.
   * constraint 가 비어 있으면 제약 없이 겹치지 않는 전체 일정을 돌려준다.
   */
  suggest: (from: string, to: string, constraint?: string) => {
    const q = new URLSearchParams({ from, to })
    if (constraint && constraint.trim()) q.set('constraint', constraint.trim())
    return api.get<ApiResponse<CalendarRoundView[]>>(`/me/calendar/suggest?${q}`, quiet)
  },
}
