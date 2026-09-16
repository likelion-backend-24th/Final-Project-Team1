import { api } from './client'
import type { ApiResponse } from '../types'
import type { OrganizerApplicationResponse } from './organizerRequest'

export const organizerAdminApi = {
  // GET /api/v1/admin/organizer-applications  (SUPER_ADMIN, PENDING 목록)
  listPending: () => api.get<ApiResponse<OrganizerApplicationResponse[]>>('/admin/organizer-applications'),
  // PATCH /api/v1/admin/organizer-applications/{id}/approve
  approve: (id: number) =>
    api.patch<ApiResponse<OrganizerApplicationResponse>>(`/admin/organizer-applications/${id}/approve`),
  // PATCH /api/v1/admin/organizer-applications/{id}/reject
  reject: (id: number, rejectReason: string) =>
    api.patch<ApiResponse<OrganizerApplicationResponse>>(`/admin/organizer-applications/${id}/reject`, { rejectReason }),
}
