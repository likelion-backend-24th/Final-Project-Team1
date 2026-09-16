import { api } from './client'
import type { ApiResponse } from '../types'

export interface OrganizerApplicationResponse {
  id: number
  userId: number
  status: string // 'PENDING' | 'APPROVED' | 'REJECTED'
  reason: string
  rejectReason: string | null
  reviewerId: number | null
  reviewedAt: string | null
  createdAt: string
}

export const organizerRequestApi = {
  // POST /api/v1/me/organizer-applications
  submit: (reason: string) =>
    api.post<ApiResponse<OrganizerApplicationResponse>>('/me/organizer-applications', { reason }),
  // GET /api/v1/me/organizer-applications
  getMyApplication: () =>
    api.get<ApiResponse<OrganizerApplicationResponse>>('/me/organizer-applications'),
}
